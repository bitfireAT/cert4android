/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import java.io.Closeable;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

/**
 * TrustManager to handle custom certificates. Communicates with
 * {@link CustomCertService} to fetch information about custom certificate
 * trustworthiness. The IPC with a service is required when multiple processes,
 * each of them with an own {@link CustomCertManager}, want to access a synchronized central
 * certificate trust store + UI (for accepting certificates etc.).
 *
 * @author Ricki Hirner
 */
public class CustomCertManager implements X509TrustManager, Closeable {

    /** how log to wait for a decision from {@link CustomCertService} */
    protected static int SERVICE_TIMEOUT = 5*60*1000;

    final Context context;

    /** for sending requests to {@link CustomCertService} */
    Messenger service;

    /** thread to receive replies from {@link CustomCertService} */
    final static HandlerThread messengerThread = new HandlerThread("CustomCertificateManager.Messenger");
    static {
        messengerThread.start();
    }
    /** messenger to receive replies from {@link CustomCertService} */
    final static Messenger messenger = new Messenger(new Handler(messengerThread.getLooper(), new MessageHandler()));

    final static AtomicInteger nextDecisionID = new AtomicInteger();
    final static SparseArray<Boolean> decisions = new SparseArray<>();
    final static Object decisionLock = new Object();

    /** system-default trust store */
    final X509TrustManager systemTrustManager;

    /** Whether to launch {@link TrustCertificateActivity} directly. The notification will always be shown. */
    public boolean appInForeground = false;

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Constants.log.fine("Connected to service");
            service = new Messenger(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            service = null;
        }
    };


    /**
     * Creates a new instance.
     * @param context used to bind to {@link CustomCertService}
     * @param trustSystemCerts whether to trust system/user-installed CAs (default trust store)
     */
    public CustomCertManager(@NonNull Context context, boolean trustSystemCerts) {
        this(context, trustSystemCerts, null);
    }

    /**
     * Creates a new instance, using a certain {@link CustomCertService} messenger (for testing)
     * @param context used to bind to {@link CustomCertService}
     * @param trustSystemCerts whether to trust system/user-installed CAs (default trust store)
     * @param service          messenger connected with {@link CustomCertService}
     */
    CustomCertManager(@NonNull Context context, boolean trustSystemCerts, @Nullable Messenger service) {
        this.context = context;

        systemTrustManager = trustSystemCerts ? CertUtils.getTrustManager(null) : null;

        if (service != null) {
            this.service = service;
            serviceConnection = null;
        } else if (!context.bindService(new Intent(context, CustomCertService.class), serviceConnection, Context.BIND_AUTO_CREATE))
            Constants.log.severe("Couldn't bind CustomCertService to context");
    }

    @Override
    public void close() {
        if (serviceConnection != null)
            context.unbindService(serviceConnection);
    }


    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("cert4android doesn't validate client certificates");
    }

    /**
     * Checks whether a certificate is trusted. If {@link #systemTrustManager} is null (because
     * system certificates are not being trusted or available), the first certificate in the chain
     * (which is the lowest one, i.e. the actual server certificate) is passed to
     * {@link CustomCertService} for further decision.
     * @param chain        certificate chain to check
     * @param authType     authentication type (ignored)
     * @throws CertificateException in case of an untrusted or questionable certificate
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        boolean trusted = false;

        if (systemTrustManager != null)
            try {
                systemTrustManager.checkServerTrusted(chain, authType);
                trusted = true;
            } catch(CertificateException e) {
                Constants.log.fine("Certificate not trusted by system");
            }

        if (!trusted)
            checkCustomTrusted(chain[0]);
    }

    protected void checkCustomTrusted(X509Certificate cert) throws CertificateException {
        Constants.log.fine("Querying custom certificate trustworthiness");

        if (service == null)
            throw new CertificateException("Custom certificate service not available");

        Message msg = Message.obtain();
        msg.what = CustomCertService.MSG_CHECK_TRUSTED;
        int id = msg.arg1 = nextDecisionID.getAndIncrement();
        msg.replyTo = messenger;

        Bundle data = new Bundle();
        data.putSerializable(CustomCertService.MSG_DATA_CERTIFICATE, cert);
        data.putBoolean(CustomCertService.MSG_DATA_APP_IN_FOREGROUND, appInForeground);
        msg.setData(data);

        try {
            service.send(msg);
        } catch(RemoteException ex) {
            throw new CertificateException("Couldn't query custom certificate trustworthiness", ex);
        }

        // wait for a reply
        long startTime = System.currentTimeMillis();
        synchronized(decisionLock) {
            while (System.currentTimeMillis() < startTime + SERVICE_TIMEOUT) {
                try {
                    decisionLock.wait(SERVICE_TIMEOUT);
                } catch(InterruptedException ex) {
                    throw new CertificateException("Trustworthiness check interrupted", ex);
                }

                Boolean decision = decisions.get(id);
                if (decision != null) {
                    decisions.delete(id);
                    if (decision)
                        // certificate trusted
                        return;
                    else
                        throw new CertificateException("Certificate not trusted");
                }
            }

            // timeout occurred
            msg = Message.obtain();
            msg.what = CustomCertService.MSG_CHECK_TRUSTED_ABORT;
            msg.arg1 = id;
            msg.replyTo = messenger;

            data = new Bundle();
            data.putSerializable(CustomCertService.MSG_DATA_CERTIFICATE, cert);
            msg.setData(data);

            try {
                service.send(msg);
            } catch(RemoteException e) {
                Constants.log.log(Level.WARNING, "Couldn't abort trustworthiness check", e);
            }

            throw new CertificateException("Timeout when waiting for certificate trustworthiness decision");
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }


    // custom methods

    public HostnameVerifier hostnameVerifier(@Nullable HostnameVerifier defaultVerifier) {
        return new CustomHostnameVerifier(defaultVerifier);
    }

    public void resetCertificates() {
        Intent intent = new Intent(context, CustomCertService.class);
        intent.setAction(CustomCertService.CMD_RESET_CERTIFICATES);
        context.startService(intent);
    }


    // Messenger for receiving replies from CustomCertificateService

    public static final int MSG_CERTIFICATE_DECISION = 0;
    // arg1: id
    // arg2: 1: trusted, 0: not trusted

    private static class MessageHandler implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            Constants.log.fine("Received reply from CustomCertificateService: " + msg);
            switch (msg.what) {
                case MSG_CERTIFICATE_DECISION:
                    synchronized(decisionLock) {
                        decisions.put(msg.arg1, msg.arg2 != 0);
                        decisionLock.notifyAll();
                    }
                    return true;
            }
            return false;
        }

    }


    // hostname verifier

    protected class CustomHostnameVerifier implements HostnameVerifier {

        final HostnameVerifier defaultVerifier;

        public CustomHostnameVerifier(HostnameVerifier defaultVerifier) {
            this.defaultVerifier = defaultVerifier;
        }

        @Override
        public boolean verify(String host, SSLSession sslSession) {
            Constants.log.fine("Verifying certificate for " + host);

            if (defaultVerifier != null && defaultVerifier.verify(host, sslSession))
                return true;

            // default hostname verifier couldn't verify the hostname →
            // accept the hostname as verified only if the certificate has been accepted be the user

            try {
                Certificate[] cert = sslSession.getPeerCertificates();
                if (cert instanceof X509Certificate[] && cert.length > 0) {
                    checkCustomTrusted((X509Certificate)cert[0]);
                    Constants.log.fine("Certificate is in custom trust store, accepting");
                    return true;
                }
            } catch(SSLPeerUnverifiedException e) {
                Constants.log.log(Level.WARNING, "Couldn't get certificate for host name verification", e);
            } catch (CertificateException ignored) {
            }

            return false;
        }

    }


}
