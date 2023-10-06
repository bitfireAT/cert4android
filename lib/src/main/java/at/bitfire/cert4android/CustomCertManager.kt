/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * TrustManager to handle custom certificates.
 *
 * @param trustSystemCerts whether system certificates will be trusted
 * @param appInForeground  - `true`: if needed, directly launches [TrustCertificateActivity] and shows notification (if possible)
 *                         - `false`: if needed, shows notification (if possible)
 *                         - `null`: non-interactive mode: does not show notification or launch activity
 */
@SuppressLint("CustomX509TrustManager")
class CustomCertManager @JvmOverloads constructor(
    context: Context,
    val trustSystemCerts: Boolean = true,
    var appInForeground: StateFlow<Boolean>?
): X509TrustManager {

    val certStore = CustomCertStore.getInstance(context)


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
        if (!certStore.isTrusted(chain, authType, trustSystemCerts, appInForeground))
            throw CertificateException("Certificate chain not trusted")
    }

    override fun getAcceptedIssuers() = arrayOf<X509Certificate>()


    /**
     * A HostnameVerifier that allows users to explicitly accept untrusted and
     * non-matching (bad hostname) certificates.
     */
    inner class CustomHostnameVerifier(
        private val defaultHostnameVerifier: HostnameVerifier? = null
    ): HostnameVerifier {

        override fun verify(hostname: String, session: SSLSession): Boolean {
            if (defaultHostnameVerifier != null && defaultHostnameVerifier.verify(hostname, session))
                return true

            Cert4Android.log.warning("Host name \"$hostname\" not verified, checking whether certificate is explicitly trusted")
            try {
                // Allow users to explicitly accept certificates that have a bad hostname here.
                (session.peerCertificates.firstOrNull() as? X509Certificate)?.let { cert ->
                    if (certStore.isTrusted(arrayOf(cert), "RSA",
                            false,      // don't trust system certificates so that the user will be asked even for system-trusted certificates
                            appInForeground))
                        return true
                }
            } catch (e: CertificateException) {
                Cert4Android.log.warning("Certificate for wrong host name \"$hostname\" not explicitly trusted by user, rejecting")
            }

            return false
        }

    }

}