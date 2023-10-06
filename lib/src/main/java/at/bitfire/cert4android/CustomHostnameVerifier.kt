package at.bitfire.cert4android

import android.content.Context
import org.apache.http.conn.ssl.StrictHostnameVerifier
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

/**
 * A HostnameVerifier that allows users to explicitly accept untrusted and
 * non-matching (bad hostname) certificates.
 *
 * @constructor Normally not called directly, but over [CustomCertManager.hostnameVerifier].
 */
class CustomHostnameVerifier(
    val certManager: CustomCertManager,
    private val defaultHostnameVerifier: HostnameVerifier? = null
): HostnameVerifier {

    override fun verify(hostname: String, session: SSLSession): Boolean {
        if (defaultHostnameVerifier != null && defaultHostnameVerifier.verify(hostname, session))
            return true

        Cert4Android.log.warning("Host name \"$hostname\" not verified, checking whether certificate is explicitly trusted")
        try {
            val cert = session.peerCertificates.firstOrNull()
            if (cert is X509Certificate) {
                // FIXME We need to allow users to explicitly accept certificates that have a bad hostname here.
                certManager.certStore.isTrusted(arrayOf(cert), "RSA", false, certManager.appInForeground)
                return true
            }
        } catch (e: CertificateException) {
            Cert4Android.log.warning("Certificate for wrong host name \"$hostname\" not explicitly trusted by user, rejecting")
        }

        return false
    }

}