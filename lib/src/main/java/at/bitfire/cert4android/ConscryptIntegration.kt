package at.bitfire.cert4android

import org.conscrypt.Conscrypt
import java.security.Security
import javax.net.ssl.SSLContext

object ConscryptIntegration {

    fun initialize() {
        // initialize Conscrypt
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        val version = Conscrypt.version()
        Cert4Android.log.info("Using Conscrypt/${version.major()}.${version.minor()}.${version.patch()} for TLS")

        val engine = SSLContext.getDefault().createSSLEngine()
        Cert4Android.log.info("Enabled protocols: ${engine.enabledProtocols.joinToString(", ")}")
        Cert4Android.log.info("Enabled ciphers: ${engine.enabledCipherSuites.joinToString(", ")}")
    }

}