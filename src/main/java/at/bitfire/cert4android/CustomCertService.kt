/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.X509Certificate
import java.util.*
import java.util.logging.Level
import javax.net.ssl.X509TrustManager

class CustomCertService: Service() {

    companion object {

        // started service

        @JvmField val CMD_CERTIFICATION_DECISION = "certDecision"
        @JvmField val EXTRA_CERTIFICATE = "certificate"
        @JvmField val EXTRA_TRUSTED = "trusted"

        @JvmField val CMD_RESET_CERTIFICATES = "resetCertificates"

        val KEYSTORE_DIR = "KeyStore"
        val KEYSTORE_NAME = "KeyStore.bks"


        // bound service; Messenger for IPC

        val MSG_CHECK_TRUSTED = 1
        val MSG_DATA_CERTIFICATE = "certificate"
        val MSG_DATA_APP_IN_FOREGROUND ="appInForeground"

        val MSG_CHECK_TRUSTED_ABORT = 2
    }

    var keyStoreFile: File? = null

    val trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())!!
    var customTrustManager: X509TrustManager? = null

    var untrustedCerts = HashSet<X509Certificate>()

    private val pendingDecisions = HashMap<X509Certificate, MutableList<ReplyInfo>>()

    override fun onCreate() {
        Constants.log.info("Creating CustomCertService")

        // initialize trustedKeyStore
        keyStoreFile = File(getDir(KEYSTORE_DIR, Context.MODE_PRIVATE), KEYSTORE_NAME)
        try {
            FileInputStream(keyStoreFile).use { trustedKeyStore.load(it, null) }
        } catch(e: Exception) {
            Constants.log.log(Level.SEVERE, "Couldn't initialize key store, creating in-memory key store", e)
            try {
                trustedKeyStore.load(null, null)
            } catch(e: Exception) {
                Constants.log.log(Level.SEVERE, "Couldn't initialize in-memory key store", e)
            }
        }

        // create custom TrustManager based on trustedKeyStore
        customTrustManager = CertUtils.getTrustManager(trustedKeyStore)
    }

    private fun inTrustStore(cert: X509Certificate) =
        try {
            trustedKeyStore.getCertificateAlias(cert) != null
        } catch(e: KeyStoreException) {
            Constants.log.log(Level.WARNING, "Couldn't query custom key store", e)
            false
        }


    // started service

    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        Constants.log.fine("Received command:" + intent)

        when (intent?.action) {
            CMD_CERTIFICATION_DECISION -> {
                val cert = intent.getSerializableExtra(EXTRA_CERTIFICATE) as X509Certificate
                onReceiveDecision(cert, intent.getBooleanExtra(EXTRA_TRUSTED, false))
            }
            CMD_RESET_CERTIFICATES -> {
                untrustedCerts.clear()
                try {
                    for (alias in trustedKeyStore.aliases())
                        trustedKeyStore.deleteEntry(alias)
                    saveKeyStore()
                } catch(e: KeyStoreException) {
                    Constants.log.log(Level.SEVERE, "Couldn't reset custom certificates", e)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun onReceiveDecision(cert: X509Certificate, trusted: Boolean) {
        // remove notification
        val nm = NotificationManagerCompat.from(this)
        nm.cancel(CertUtils.getTag(cert), Constants.NOTIFICATION_CERT_DECISION)

        // put into trust store, if trusted
        if (trusted) {
            untrustedCerts.remove(cert)

            try {
                trustedKeyStore.setCertificateEntry(cert.subjectDN.name, cert)
                saveKeyStore()
            } catch(e: KeyStoreException) {
                Constants.log.log(Level.SEVERE, "Couldn't add certificate into key store", e)
            }
        } else
            untrustedCerts.add(cert)

        // notify receivers which are waiting for a decision
        pendingDecisions[cert]?.let { receivers ->
            for ((messenger, id) in receivers) {
                val message = Message.obtain()
                message.what = CustomCertManager.MSG_CERTIFICATE_DECISION
                message.arg1 = id
                message.arg2 = if (trusted) 1 else 0
                try {
                    messenger.send(message)
                } catch(e: RemoteException) {
                    Constants.log.log(Level.WARNING, "Couldn't forward decision to CustomCertManager", e)
                }
            }
            pendingDecisions.remove(cert)
        }
    }

    private fun saveKeyStore() {
        Constants.log.fine("Saving custom certificate key store to $keyStoreFile")
        try {
            FileOutputStream(keyStoreFile).use { trustedKeyStore.store(it, null) }
        } catch(e: Exception) {
            Constants.log.log(Level.SEVERE, "Couldn't save custom certificate key store", e)
        }
    }


    // bound service; Messenger for IPC

    val messenger = Messenger(MessageHandler(this))
    override fun onBind(intent: Intent?) = messenger.binder!!

    private class MessageHandler(
            service: CustomCertService
    ): Handler() {
        val serviceRef = WeakReference<CustomCertService>(service)

        override fun handleMessage(msg: Message) {
            val service = serviceRef.get()
            if (service == null) {
                Constants.log.warning("Couldn't handle message: service not available")
                return
            }

            Constants.log.info("Handling request: $msg")
            val id = msg.arg1

            val data = msg.data
            val cert = data.getSerializable(MSG_DATA_CERTIFICATE) as X509Certificate

            val replyInfo = ReplyInfo(msg.replyTo, id)

            when (msg.what) {
                MSG_CHECK_TRUSTED -> {
                    service.pendingDecisions[cert]?.let { reply ->
                        // there's already a pending decision for this certificate, just add this reply messenger
                        reply += replyInfo
                        return
                    }

                    /* no pending decision for this certificate:
                       1. check whether it's known as trusted or non-trusted – in this case, send a reply instantly
                       2. otherwise, create a pending decision
                     */
                    when {
                        service.untrustedCerts.contains(cert) -> {
                            Constants.log.fine("Certificate is cached as untrusted")
                            try {
                                msg.replyTo.send(obtainMessage(CustomCertManager.MSG_CERTIFICATE_DECISION, id, 0))
                            } catch(e: RemoteException) {
                                Constants.log.log(Level.WARNING, "Couldn't send distrust information to CustomCertManager", e)
                            }

                        }
                        service.inTrustStore(cert) -> {
                            try {
                                msg.replyTo.send(obtainMessage(CustomCertManager.MSG_CERTIFICATE_DECISION, id, 1))
                            } catch(e: RemoteException) {
                                Constants.log.log(Level.WARNING, "Couldn't send trust information to CustomCertManager", e)
                            }
                        }
                        else -> {
                            val receivers = LinkedList<ReplyInfo>()
                            receivers += replyInfo
                            service.pendingDecisions.put(cert, receivers)

                            val decisionIntent = Intent(service, TrustCertificateActivity::class.java)
                            decisionIntent.putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, cert)

                            val notify = NotificationCompat.Builder(service)
                                    .setSmallIcon(R.drawable.ic_lock_open_white)
                                    .setContentTitle(service.getString(R.string.certificate_notification_connection_security))
                                    .setContentText(service.getString(R.string.certificate_notification_user_interaction))
                                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setOngoing(true)
                                    .setContentIntent(PendingIntent.getActivity(service, id, decisionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                                    .build()
                            val nm = NotificationManagerCompat.from(service)
                            nm.notify(CertUtils.getTag(cert), Constants.NOTIFICATION_CERT_DECISION, notify)

                            if (data.getBoolean(MSG_DATA_APP_IN_FOREGROUND)) {
                                decisionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                service.startActivity(decisionIntent)
                            }
                        }
                    }
                }

                MSG_CHECK_TRUSTED_ABORT -> {
                    val replyInfos = service.pendingDecisions[cert]

                    // remove decision receivers from pending decision
                    if (replyInfos != null) {
                        val it = replyInfos.listIterator()
                        while (it.hasNext())
                            if (it.next() == replyInfo)
                                it.remove()
                    }

                    if (replyInfos == null || replyInfos.isEmpty()) {
                        // no more decision receivers, remove pending decision
                        service.pendingDecisions.remove(cert)

                        val nm = NotificationManagerCompat.from(service)
                        nm.cancel(CertUtils.getTag(cert), Constants.NOTIFICATION_CERT_DECISION)
                    }
                }
            }
        }
    }


    // data classes

    internal data class ReplyInfo(
            val messenger: Messenger,
            val id: Int
    ) {

        override fun hashCode() = messenger.hashCode() xor id

        override fun equals(other: Any?): Boolean {
            return if (other is ReplyInfo) {
                other.messenger == messenger && other.id == id
            } else
                false
        }

    }

}
