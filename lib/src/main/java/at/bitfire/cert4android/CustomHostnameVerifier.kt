package at.bitfire.cert4android

import android.content.Context
import org.apache.http.conn.ssl.StrictHostnameVerifier
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

class CustomHostnameVerifier(
    context: Context,
    private val defaultHostnameVerifier: HostnameVerifier? = null
): HostnameVerifier {

    private val customCertStore = CustomCertStore.getInstance(context)

    override fun verify(hostname: String, session: SSLSession): Boolean {
        if (defaultHostnameVerifier != null && defaultHostnameVerifier.verify(hostname, session))
            return true

        Cert4Android.log.warning("Host name \"$hostname\" not verified, checking whether certificate is explicitly trusted")

        val cert = session.peerCertificates.firstOrNull()
        if (cert is X509Certificate && customCertStore.isTrustedByUser(cert))
                return true

        return false
    }

}