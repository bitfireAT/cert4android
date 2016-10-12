/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.net.ssl.X509TrustManager;

public class CustomCertService extends Service {

    protected static final String CMD_CERTIFICATION_DECISION = "certDecision";
    protected static final String EXTRA_CERTIFICATE = "certificate";
    protected static final String EXTRA_TRUSTED = "trusted";

    protected static final String CMD_RESET_CERTIFICATES = "resetCertificates";

    public static final String
            KEYSTORE_DIR = "KeyStore",
            KEYSTORE_NAME = "KeyStore.bks";
    File keyStoreFile;

    KeyStore trustedKeyStore;
    X509TrustManager customTrustManager;

    Set<X509Certificate> untrustedCerts = new HashSet<>();

    final Map<X509Certificate, List<ReplyInfo>> pendingDecisions = new HashMap<>();

    @Override
    public void onCreate() {
        Constants.log.info("Creating CustomCertService");
        keyStoreFile = new File(getDir(KEYSTORE_DIR, Context.MODE_PRIVATE), KEYSTORE_NAME);
        try {
            trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());

            InputStream is;
            try {
                is = new FileInputStream(keyStoreFile);
            } catch(FileNotFoundException e) {
                Constants.log.fine("No custom keystore found");
                is = null;
            }
            trustedKeyStore.load(is, null);
            customTrustManager = CertUtils.getTrustManager(trustedKeyStore);
        } catch(IOException|KeyStoreException|NoSuchAlgorithmException|CertificateException e) {
            Constants.log.log(Level.SEVERE, "Couldn't initialize key store, creating in-memory key store", e);
            try {
                trustedKeyStore.load(null, null);
            } catch(NoSuchAlgorithmException|CertificateException|IOException e2) {
                Constants.log.log(Level.SEVERE, "Couldn't initialize in-memory key store", e2);
            }
        }
    }

    boolean inTrustStore(X509Certificate cert) {
        try {
            return trustedKeyStore.getCertificateAlias(cert) != null;
        } catch(KeyStoreException e) {
            Constants.log.log(Level.WARNING, "Couldn't query custom key store", e);
            return false;
        }
    }

    // started service

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        Constants.log.fine("Received command:" + intent);

        if (intent.getAction() == null)
            // should not happen, but happens
            return START_NOT_STICKY;

        switch (intent.getAction()) {
            case CMD_CERTIFICATION_DECISION:
                onReceiveDecision(
                        (X509Certificate)intent.getSerializableExtra(EXTRA_CERTIFICATE),
                        intent.getBooleanExtra(EXTRA_TRUSTED, false)
                );
                break;
            case CMD_RESET_CERTIFICATES:
                untrustedCerts.clear();
                try {
                    for (String alias : Collections.list(trustedKeyStore.aliases()))
                        trustedKeyStore.deleteEntry(alias);
                    saveKeyStore();
                } catch(KeyStoreException e) {
                    Constants.log.log(Level.SEVERE, "Couldn't reset custom certificates", e);
                }
        }
        return START_NOT_STICKY;
    }

    protected void onReceiveDecision(X509Certificate cert, boolean trusted) {
        // remove notification
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        nm.cancel(CertUtils.getTag(cert), Constants.NOTIFICATION_CERT_DECISION);

        // put into trust store, if trusted
        if (trusted) {
            untrustedCerts.remove(cert);

            try {
                trustedKeyStore.setCertificateEntry(cert.getSubjectDN().getName(), cert);
            } catch(KeyStoreException e) {
                Constants.log.log(Level.SEVERE, "Couldn't add certificate into key store", e);
            }
            saveKeyStore();
        } else
            untrustedCerts.add(cert);

        // notify receivers which are waiting for a decision
        List<ReplyInfo> receivers = pendingDecisions.get(cert);
        if (receivers != null) {
            for (ReplyInfo receiver : receivers) {
                Message message = Message.obtain();
                message.what = CustomCertManager.MSG_CERTIFICATE_DECISION;
                message.arg1 = receiver.id;
                message.arg2 = trusted ? 1 : 0;
                try {
                    receiver.messenger.send(message);
                } catch(RemoteException e) {
                    Constants.log.log(Level.WARNING, "Couldn't forward decision to CustomCertManager", e);
                }
            }

            pendingDecisions.remove(cert);
        }
    }

    protected void saveKeyStore() {
        try {
            Constants.log.fine("Saving custom certificate key store to " + keyStoreFile);
            OutputStream os = new FileOutputStream(keyStoreFile);
            trustedKeyStore.store(os, null);
        } catch(IOException|KeyStoreException|NoSuchAlgorithmException|CertificateException e) {
            Constants.log.log(Level.SEVERE, "Couldn't save custom certificate key store", e);
        }
    }


    // bound service; Messenger for IPC

    public static final int MSG_CHECK_TRUSTED = 1;
    public static final String
            MSG_DATA_CERTIFICATE = "certificate",
            MSG_DATA_APP_IN_FOREGROUND ="appInForeground";
    public static final int MSG_CHECK_TRUSTED_ABORT = 2;

    final Messenger messenger = new Messenger(new MessageHandler(this));

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    protected static class MessageHandler extends Handler {
        private final WeakReference<CustomCertService> serviceRef;

        MessageHandler(CustomCertService service) {
            serviceRef = new WeakReference<CustomCertService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            CustomCertService service = serviceRef.get();
            if (service == null) {
                Constants.log.warning("Couldn't handle message: service not available");
                return;
            }

            Constants.log.info("Handling request: " + msg);
            int id = msg.arg1;

            Bundle data = msg.getData();
            X509Certificate cert = (X509Certificate)data.getSerializable(MSG_DATA_CERTIFICATE);

            ReplyInfo replyInfo = new ReplyInfo(msg.replyTo, id);

            switch (msg.what) {
                case MSG_CHECK_TRUSTED:

                    List<ReplyInfo> reply = service.pendingDecisions.get(cert);
                    if (reply != null) {
                        // there's already a pending decision for this certificate, just add this reply messenger
                        reply.add(replyInfo);

                    } else {
                        /* no pending decision for this certificate:
                           1. check whether it's known as trusted or non-trusted – in this case, send a reply instantly
                           2. otherwise, create a pending decision
                         */

                        if (service.untrustedCerts.contains(cert)) {
                            Constants.log.fine("Certificate is cached as untrusted");
                            try {
                                msg.replyTo.send(obtainMessage(CustomCertManager.MSG_CERTIFICATE_DECISION, id, 0));
                            } catch(RemoteException e) {
                                Constants.log.log(Level.WARNING, "Couldn't send distrust information to CustomCertManager", e);
                            }

                        } else if (service.inTrustStore(cert)) {
                            try {
                                msg.replyTo.send(obtainMessage(CustomCertManager.MSG_CERTIFICATE_DECISION, id, 1));
                            } catch(RemoteException e) {
                                Constants.log.log(Level.WARNING, "Couldn't send trust information to CustomCertManager", e);
                            }

                        } else {
                            List<ReplyInfo> receivers = new LinkedList<>();
                            receivers.add(replyInfo);
                            service.pendingDecisions.put(cert, receivers);

                            Intent decisionIntent = new Intent(service, TrustCertificateActivity.class);
                            decisionIntent.putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, cert);

                            Notification notify = new NotificationCompat.Builder(service)
                                    .setSmallIcon(R.drawable.ic_lock_open_white)
                                    .setContentTitle(service.getString(R.string.certificate_notification_connection_security))
                                    .setContentText(service.getString(R.string.certificate_notification_user_interaction))
                                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setOngoing(true)
                                    .setContentIntent(PendingIntent.getActivity(service, id, decisionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                                    .build();
                            NotificationManagerCompat nm = NotificationManagerCompat.from(service);
                            nm.notify(CertUtils.getTag(cert), Constants.NOTIFICATION_CERT_DECISION, notify);

                            if (data.getBoolean(MSG_DATA_APP_IN_FOREGROUND)) {
                                decisionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                service.startActivity(decisionIntent);
                            }
                        }
                    }
                    break;

                case MSG_CHECK_TRUSTED_ABORT:
                    List<ReplyInfo> replyInfos = service.pendingDecisions.get(cert);

                    // remove decision receivers from pending decision
                    if (replyInfos != null) {
                        Iterator<ReplyInfo> it = replyInfos.iterator();
                        while (it.hasNext())
                            if (replyInfo.equals(it.next()))
                                it.remove();
                    }

                    if (replyInfos == null || replyInfos.isEmpty()) {
                        // no more decision receivers, remove pending decision
                        service.pendingDecisions.remove(cert);

                        NotificationManagerCompat nm = NotificationManagerCompat.from(service);
                        nm.cancel(CertUtils.getTag(cert), Constants.NOTIFICATION_CERT_DECISION);
                    }
                    break;
            }
        }
    }


    // data classes

    protected static class ReplyInfo {

        final Messenger messenger;
        final int id;

        ReplyInfo(Messenger messenger, int id) {
            this.messenger = messenger;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ReplyInfo) {
                ReplyInfo replyInfo = (ReplyInfo)obj;
                return replyInfo.messenger.equals(messenger) && replyInfo.id == id;
            } else
                return false;
        }

    }

}
