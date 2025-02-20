/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * TrustManager to handle custom certificates.
 *
 * Initializes Conscrypt when it is first loaded.
 *
 * @param trustSystemCerts whether system certificates will be trusted
 * @param getUserDecision  anonymous function to retrieve user decision on whether to trust a
 *                         certificate; should return *true* if the user trusts the certificate
 */
@SuppressLint("CustomX509TrustManager")
class CustomCertManager @JvmOverloads constructor(
    context: Context,
    scope: CoroutineScope,
    val trustSystemCerts: Boolean = true,
    private val getUserDecision: suspend (X509Certificate) -> Boolean
): X509TrustManager {

    val certStore = CustomCertStore.getInstance(context, scope)


    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        throw CertificateException("cert4android doesn't validate client certificates")
    }

    /**
     * Checks whether a certificate is trusted. Allows user to explicitly accept untrusted certificates.
     *
     * @param chain        certificate chain to check
     * @param authType     authentication type (ignored)
     *
     * @throws CertificateException in case of an untrusted or questionable certificate
     */
    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (!certStore.isTrusted(chain, authType, trustSystemCerts, getUserDecision))
            throw CertificateException("Certificate chain not trusted")
    }

    override fun getAcceptedIssuers() = arrayOf<X509Certificate>()


    /**
     * A HostnameVerifier that allows users to explicitly accept untrusted and
     * non-matching (bad hostname) certificates.
     */
    inner class HostnameVerifier(
        private val defaultHostnameVerifier: javax.net.ssl.HostnameVerifier? = null
    ): javax.net.ssl.HostnameVerifier {

        override fun verify(hostname: String, session: SSLSession): Boolean {
            if (defaultHostnameVerifier != null && defaultHostnameVerifier.verify(hostname, session))
                // default HostnameVerifier says trusted → OK
                return true

            Cert4Android.log.warning("Host name \"$hostname\" not verified, checking whether certificate is explicitly trusted")
            // Allow users to explicitly accept certificates that have a bad hostname here
            (session.peerCertificates.firstOrNull() as? X509Certificate)?.let { cert ->
                // Check without trusting system certificates so that the user will be asked even for system-trusted certificates
                if (certStore.isTrusted(arrayOf(cert), "RSA", false, getUserDecision))
                    return true
            }

            return false
        }

    }


    companion object {

        init {
            // On first load of this class, initialize Conscrypt.
            ConscryptIntegration.initialize()
        }

    }

}