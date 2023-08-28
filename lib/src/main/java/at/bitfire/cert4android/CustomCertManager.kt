/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.conscrypt.Conscrypt
import java.io.Closeable
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * TrustManager to handle custom certificates. Communicates with
 * [CustomCertService] (singleton) to fetch information about custom certificate
 * trustworthiness. The service is required when multiple threads,
 * each of them with an own [CustomCertManager], want to access a synchronized central
 * certificate trust store + UI (for accepting certificates etc.).
 *
 * @param context used to bind to [CustomCertService]
 * @param interactive true: users will be notified in case of unknown certificates;
 *                    false: unknown certificates will be rejected (only uses custom certificate key store)
 * @param trustSystemCerts whether system certificates will be trusted
 * @param appInForeground  Whether to launch [TrustCertificateActivity] directly. The notification will always be shown.
 *
 * @constructor Creates a new instance, using a certain [CustomCertService] messenger (for testing).
 * Must not be run from the main thread because this constructor may request binding to [CustomCertService].
 * The actual binding code is called by the looper in the main thread, so waiting for the
 * service would block forever.
 *
 * @throws IllegalStateException if run from main thread
 */
@SuppressLint("CustomX509TrustManager")
class CustomCertManager @JvmOverloads constructor(
    private val context: Context,
    val interactive: Boolean = true,
    trustSystemCerts: Boolean = true,

    @Volatile
    var appInForeground: Boolean = false
): X509TrustManager, Closeable {

    companion object {

        /** how long to wait for a decision from [CustomCertService] before giving up temporarily */
        var SERVICE_TIMEOUT: Long = 3*60*1000

        fun resetCertificates(context: Context): Boolean {
            val intent = Intent(context, CustomCertService::class.java)
            intent.action = CustomCertService.CMD_RESET_CERTIFICATES
            return context.startService(intent) != null
        }

    }

    var service: ICustomCertService? = null
    private var serviceConn: ServiceConnection? = null
    private var serviceLock = Object()

    /** system-default trust store */
    private val systemTrustManager: X509TrustManager? =
        if (trustSystemCerts) Conscrypt.getDefaultX509TrustManager() else null


    init {
        val newServiceConn = object: ServiceConnection {
            override fun onServiceConnected(className: ComponentName, binder: IBinder) {
                Cert4Android.log.fine("Connected to service")
                synchronized(serviceLock) {
                    this@CustomCertManager.service = binder as ICustomCertService
                    serviceLock.notify()
                }
            }

            override fun onServiceDisconnected(className: ComponentName) {
                synchronized(serviceLock) {
                    this@CustomCertManager.service = null
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper())
            // service is created after bindService() by code running in looper, so this would block
            throw IllegalStateException("Must not be run in main thread")

        Cert4Android.log.fine("Binding to service")
        if (context.bindService(Intent(context, CustomCertService::class.java), newServiceConn, Context.BIND_AUTO_CREATE)) {
            serviceConn = newServiceConn

            Cert4Android.log.fine("Waiting for service to be bound")
            synchronized(serviceLock) {
                while (service == null)
                    try {
                        serviceLock.wait()
                    } catch(_: InterruptedException) {
                    }
            }
        } else
            Cert4Android.log.severe("Couldn't bind CustomCertService to context")
    }

    override fun close() {
        serviceConn?.let {
            try {
                context.unbindService(it)
            } catch (e: Exception) {
                Cert4Android.log.log(Level.FINE, "Couldn't unbind CustomCertService", e)
            }
            serviceConn = null
        }
    }


    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        throw CertificateException("cert4android doesn't validate client certificates")
    }

    /**
     * Checks whether a certificate is trusted. If [systemTrustManager] is null (because
     * system certificates are not being trusted or available), the first certificate in the chain
     * (which is the lowest one, i.e. the actual server certificate) is passed to
     * [CustomCertService] for further decision.
     *
     * @param chain        certificate chain to check
     * @param authType     authentication type (ignored)
     *
     * @throws CertificateException in case of an untrusted or questionable certificate
     */
    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        var trusted = false

        systemTrustManager?.let { trustManager ->
            try {
                trustManager.checkServerTrusted(chain, authType)
                trusted = true
            } catch(e: CertificateException) {
                Cert4Android.log.log(Level.INFO, "Certificate not trusted by system, checking ourselves", e)
            }
        }

        if (!trusted) runBlocking {
            // not trusted by system, let's check ourselves
            checkCustomTrusted(chain[0])
        }
    }

    internal suspend fun checkCustomTrusted(cert: X509Certificate) {
        val svc = service ?: throw CertificateException("Not bound to CustomCertService")

        val isValid = try {
            withTimeoutOrNull(SERVICE_TIMEOUT) { svc.checkTrusted(cert, interactive, appInForeground) }
        } catch(e: Exception) {
            throw CertificateException("Couldn't check certificate", e)
        }

        when (isValid) {
            null -> {
                svc.abortCheck(cert)
                throw CertificateException("Timeout when waiting for certificate trustworthiness decision")
            }

            true -> { /* OK */ }
            false -> throw CertificateException("Certificate not accepted by CustomCertService")
        }
    }

    override fun getAcceptedIssuers() = arrayOf<X509Certificate>()


    // custom methods

    fun hostnameVerifier(defaultVerifier: HostnameVerifier?) = CustomHostnameVerifier(defaultVerifier)


    // hostname verifier

    inner class CustomHostnameVerifier(
            private val defaultVerifier: HostnameVerifier?
    ): HostnameVerifier {

        override fun verify(host: String, sslSession: SSLSession): Boolean {
            Cert4Android.log.fine("Verifying certificate for $host")

            if (defaultVerifier?.verify(host, sslSession) == true)
                return true

            // default hostname verifier couldn't verify the hostname →
            // accept the hostname as verified only if the certificate has been accepted be the user

            try {
                val cert = sslSession.peerCertificates
                if (cert.isNotEmpty() && cert[0] is X509Certificate) {
                    runBlocking { checkCustomTrusted(cert[0] as X509Certificate) }
                    Cert4Android.log.fine("Certificate is in custom trust store, accepting")
                    return true
                }
            } catch(e: SSLPeerUnverifiedException) {
                Cert4Android.log.log(Level.WARNING, "Couldn't get certificate for host name verification", e)
            } catch (ignored: CertificateException) {
            }

            return false
        }

    }

}
