/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.conscrypt.Conscrypt
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.util.logging.Level
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The service which manages the certificates. Communications with
 * the [CustomCertManager]s over IPC. Initializes Conscrypt when class is loaded.
 *
 * This services is both a started and a bound service.
 */
class CustomCertService: Service() {

    companion object {
        /**
         * Command when used as started service to accept/reject an open certificate decision.
         * Usually sent by a notification action or [TrustCertificateActivity].
         */
        const val CMD_CERTIFICATION_DECISION = "certificateDecision"
        /**
         * Command when used as a started service to remove all known certificates.
         * Resets the state of all previously accepted and rejected certificates.
         */
        const val CMD_RESET_CERTIFICATES = "resetCertificates"

        const val EXTRA_CERTIFICATE = "certificate"
        const val EXTRA_TRUSTED = "trusted"

        const val KEYSTORE_DIR = "KeyStore"
        const val KEYSTORE_NAME = "KeyStore.bks"

        init {
            // initialize Conscrypt
            Security.insertProviderAt(Conscrypt.newProvider(), 1)

            val version = Conscrypt.version()
            Log.i(Cert4Android.TAG, "Using Conscrypt/${version.major()}.${version.minor()}.${version.patch()} for TLS")
            val engine = SSLContext.getDefault().createSSLEngine()
            Log.i(Cert4Android.TAG, "Enabled protocols: ${engine.enabledProtocols.joinToString(", ")}")
            Log.i(Cert4Android.TAG, "Enabled ciphers: ${engine.enabledCipherSuites.joinToString(", ")}")
        }

    }

    private lateinit var keyStoreFile: File

    private val certFactory = CertificateFactory.getInstance("X.509")
    private val trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())!!
    private var customTrustManager: X509TrustManager? = null

    private var untrustedCerts = HashSet<X509Certificate>()

    @Suppress("DEPRECATION")
    @Deprecated("No longer using IOnCertificateDecision")
    private val pendingDecisions = mutableMapOf<X509Certificate, MutableList<IOnCertificateDecision>>()

    private val pendingCompletables = mutableMapOf<X509Certificate, MutableList<CompletableDeferred<Boolean>>>()


    override fun onCreate() {
        Cert4Android.log.info("CustomCertService created")

        // initialize trustedKeyStore
        keyStoreFile = File(getDir(KEYSTORE_DIR, Context.MODE_PRIVATE), KEYSTORE_NAME)
        try {
            FileInputStream(keyStoreFile).use {
                trustedKeyStore.load(it, null)
                Cert4Android.log.fine("Loaded ${trustedKeyStore.size()} trusted certificate(s)")
            }
        } catch(e: Exception) {
            Cert4Android.log.log(Level.INFO, "No key store for trusted certifcates (yet); creating in-memory key store.")
            try {
                trustedKeyStore.load(null, null)
            } catch(e: Exception) {
                Cert4Android.log.log(Level.SEVERE, "Couldn't initialize in-memory key store", e)
            }
        }

        // create custom TrustManager based on trustedKeyStore
        customTrustManager = CertUtils.getTrustManager(trustedKeyStore)
    }

    override fun onDestroy() {
        Cert4Android.log.info("CustomCertService destroyed")
    }

    private fun inTrustStore(cert: X509Certificate) =
        try {
            trustedKeyStore.getCertificateAlias(cert) != null
        } catch(e: KeyStoreException) {
            Cert4Android.log.log(Level.WARNING, "Couldn't query custom key store", e)
            false
        }


    // started service

    @MainThread
    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        Cert4Android.log.fine("Received command: $intent")

        when (intent?.action) {
            CMD_CERTIFICATION_DECISION -> {
                val raw = intent.getByteArrayExtra(EXTRA_CERTIFICATE)
                try {
                    val cert = certFactory.generateCertificate(ByteArrayInputStream(raw)) as X509Certificate
                    onReceiveDecision(cert, intent.getBooleanExtra(EXTRA_TRUSTED, false))
                } catch (e: Exception) {
                    Cert4Android.log.log(Level.SEVERE, "Couldn't process certificate", e)
                }
            }
            CMD_RESET_CERTIFICATES -> {
                untrustedCerts.clear()
                try {
                    for (alias in trustedKeyStore.aliases())
                        trustedKeyStore.deleteEntry(alias)
                    saveKeyStore()
                } catch(e: KeyStoreException) {
                    Cert4Android.log.log(Level.SEVERE, "Couldn't reset custom certificates", e)
                }
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun onReceiveDecision(cert: X509Certificate, trusted: Boolean) {
        // remove notification
        val nm = NotificationUtils.createChannels(this)
        nm.cancel(CertUtils.getTag(cert), Cert4Android.NOTIFICATION_CERT_DECISION)

        // put into trust store, if trusted
        if (trusted) {
            untrustedCerts.remove(cert)

            try {
                // This is the key which is used to store the certificate. If the CN is used,
                // there can be only one certificate per CN (which is not always desired),
                // so we use the SHA-256 fingerprint as a key.
                val certKey = CertUtils.fingerprint(cert, MGF1ParameterSpec.SHA256.digestAlgorithm)
                trustedKeyStore.setCertificateEntry(certKey, cert)
                saveKeyStore()
            } catch(e: KeyStoreException) {
                Cert4Android.log.log(Level.SEVERE, "Couldn't add certificate into key store", e)
            }
        } else {
            untrustedCerts.add(cert)
            Toast.makeText(this, R.string.service_rejected_temporarily, Toast.LENGTH_LONG).show()
        }

        // notify receivers which are waiting for a decision
        @Suppress("DEPRECATION")
        pendingDecisions[cert]?.let { callbacks ->
            Cert4Android.log.fine("Notifying ${callbacks.size} certificate decision listener(s)")
            callbacks.forEach {
                if (trusted)
                    it.accept()
                else
                    it.reject()
            }
            pendingDecisions -= cert
        }
        pendingCompletables[cert]?.let { list ->
            Cert4Android.log.fine("Notifying ${list.size} pending certificate completable decision listener(s)")
            list.forEach {
                it.complete(trusted)
            }
            pendingCompletables -= cert
        }
    }

    private fun saveKeyStore() {
        Cert4Android.log.fine("Saving custom certificate key store to $keyStoreFile")
        try {
            FileOutputStream(keyStoreFile).use { trustedKeyStore.store(it, null) }
        } catch(e: Exception) {
            Cert4Android.log.log(Level.SEVERE, "Couldn't save custom certificate key store", e)
        }
    }


    // bound service

    private val binder = object: ICustomCertService, Binder() {

        @Deprecated(
            message = "Callbacks no longer used",
            replaceWith = ReplaceWith("checkTrusted(rawCert, interactive, foreground)")
        )
        @Suppress("DEPRECATION")
        override fun checkTrusted(rawCert: ByteArray, interactive: Boolean, foreground: Boolean, callback: IOnCertificateDecision) {
            val cert: X509Certificate? = try {
                certFactory.generateCertificate(ByteArrayInputStream(rawCert)) as? X509Certificate
            } catch(e: Exception) {
                Cert4Android.log.log(Level.SEVERE, "Couldn't handle certificate", e)
                null
            }
            if (cert == null) {
                callback.reject()
                return
            }

            // if there's already a pending decision for this certificate, just add this callback
            pendingDecisions[cert]?.let { callbacks ->
                callbacks += callback
                return
            }

            when {
                untrustedCerts.contains(cert) -> {
                    Cert4Android.log.fine("Certificate is cached as untrusted, rejecting")
                    callback.reject()
                }
                inTrustStore(cert) -> {
                    Cert4Android.log.fine("Certificate is cached as trusted, accepting")
                    callback.accept()
                }
                else -> {
                    if (interactive) {
                        Cert4Android.log.fine("Certificate not known and running in interactive mode, asking user")

                        // remember pending decision
                        pendingDecisions[cert] = mutableListOf(callback)

                        val decisionIntent = Intent(this@CustomCertService, TrustCertificateActivity::class.java)
                        decisionIntent.putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, rawCert)

                        val rejectIntent = Intent(this@CustomCertService, CustomCertService::class.java)
                        with(rejectIntent) {
                            action = CMD_CERTIFICATION_DECISION
                            putExtra(EXTRA_CERTIFICATE, rawCert)
                            putExtra(EXTRA_TRUSTED, false)
                        }

                        val id = rawCert.contentHashCode()
                        val notify = NotificationCompat.Builder(this@CustomCertService, NotificationUtils.CHANNEL_CERTIFICATES)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setSmallIcon(R.drawable.ic_lock_open_white)
                                .setContentTitle(this@CustomCertService.getString(R.string.certificate_notification_connection_security))
                                .setContentText(this@CustomCertService.getString(R.string.certificate_notification_user_interaction))
                                .setSubText(cert.subjectDN.name)
                                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                                .setContentIntent(PendingIntent.getActivity(this@CustomCertService, id, decisionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                                .setDeleteIntent(PendingIntent.getService(this@CustomCertService, id, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                                .build()
                        val nm = NotificationUtils.createChannels(this@CustomCertService)
                        if (ActivityCompat.checkSelfPermission(this@CustomCertService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                            nm.notify(CertUtils.getTag(cert), Cert4Android.NOTIFICATION_CERT_DECISION, notify)
                        else
                            Cert4Android.log.warning("Couldn't show certificate notification (missing notification permission)")

                        if (foreground) {
                            decisionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(decisionIntent)
                        }

                    } else {
                        Cert4Android.log.fine("Certificate not known and running in non-interactive mode, rejecting")
                        callback.reject()
                    }
                }
            }
        }

        override fun checkTrusted(rawCert: ByteArray, interactive: Boolean, foreground: Boolean): CompletableDeferred<Boolean> {
            val completable = CompletableDeferred(false)

            val cert: X509Certificate? = try {
                certFactory.generateCertificate(ByteArrayInputStream(rawCert)) as? X509Certificate
            } catch(e: Exception) {
                Cert4Android.log.log(Level.SEVERE, "Couldn't handle certificate", e)
                null
            }
            if (cert == null) {
                completable.complete(false)
                return completable
            }

            // if there's already a pending decision for this certificate, just add this callback
            pendingCompletables[cert]?.let { list ->
                list += completable
                return completable
            }

            when {
                untrustedCerts.contains(cert) -> {
                    Cert4Android.log.fine("Certificate is cached as untrusted, rejecting")
                    completable.complete(false)
                }
                inTrustStore(cert) -> {
                    Cert4Android.log.fine("Certificate is cached as trusted, accepting")
                    completable.complete(true)
                }
                else -> {
                    if (interactive) {
                        Cert4Android.log.fine("Certificate not known and running in interactive mode, asking user")

                        // remember pending decision
                        pendingCompletables[cert] = mutableListOf(completable)

                        val decisionIntent = Intent(this@CustomCertService, TrustCertificateActivity::class.java)
                        decisionIntent.putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, rawCert)

                        val rejectIntent = Intent(this@CustomCertService, CustomCertService::class.java)
                        with(rejectIntent) {
                            action = CMD_CERTIFICATION_DECISION
                            putExtra(EXTRA_CERTIFICATE, rawCert)
                            putExtra(EXTRA_TRUSTED, false)
                        }

                        val id = rawCert.contentHashCode()
                        val notify = NotificationCompat.Builder(this@CustomCertService, NotificationUtils.CHANNEL_CERTIFICATES)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setSmallIcon(R.drawable.ic_lock_open_white)
                            .setContentTitle(this@CustomCertService.getString(R.string.certificate_notification_connection_security))
                            .setContentText(this@CustomCertService.getString(R.string.certificate_notification_user_interaction))
                            .setSubText(cert.subjectDN.name)
                            .setCategory(NotificationCompat.CATEGORY_SERVICE)
                            .setContentIntent(PendingIntent.getActivity(this@CustomCertService, id, decisionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                            .setDeleteIntent(PendingIntent.getService(this@CustomCertService, id, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                            .build()
                        val nm = NotificationUtils.createChannels(this@CustomCertService)
                        if (ActivityCompat.checkSelfPermission(this@CustomCertService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                            nm.notify(CertUtils.getTag(cert), Cert4Android.NOTIFICATION_CERT_DECISION, notify)
                        else
                            Cert4Android.log.warning("Couldn't show certificate notification (missing notification permission)")

                        if (foreground) {
                            decisionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(decisionIntent)
                        }

                    } else {
                        Cert4Android.log.fine("Certificate not known and running in non-interactive mode, rejecting")
                        completable.complete(false)
                    }
                }
            }

            return completable
        }

        @Deprecated(
            message = "Callbacks no longer used",
            replaceWith = ReplaceWith("abortCheck()")
        )
        @Suppress("DEPRECATION")
        override fun abortCheck(callback: IOnCertificateDecision) {
            for ((cert, list) in pendingDecisions) {
                list.removeAll { it == callback }
                if (list.isEmpty())
                    pendingDecisions -= cert
            }
        }

        override fun abortCheck(completable: CompletableDeferred<Boolean>) {
            for ((cert, list) in pendingCompletables) {
                list.removeAll { it == completable }
                if (list.isEmpty())
                    pendingCompletables -= cert
            }
        }

    }

    override fun onBind(intent: Intent?): IBinder = binder

}
