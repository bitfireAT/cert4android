package at.bitfire.cert4android

import android.content.Context
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

class CustomHostnameVerifier(
    context: Context,
    private val defaultHostnameVerifier: HostnameVerifier?
): HostnameVerifier {

    private val customCertStore = CustomCertStore.getInstance(context)

    override fun verify(hostname: String, session: SSLSession): Boolean {
        if (defaultHostnameVerifier != null && defaultHostnameVerifier.verify(hostname, session))
            return true

        // default hostname verifier couldn't verify the hostname →
        // accept the hostname as verified only if the certificate has been explicitly accepted be the user

        val cert = session.peerCertificates.firstOrNull()
        if (cert is X509Certificate && customCertStore.isTrustedByUser(cert))
                return true

        return false
    }

}