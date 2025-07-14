package at.bitfire.cert4android

import org.conscrypt.Conscrypt
import java.security.Security
import java.util.logging.Logger
import javax.net.ssl.SSLContext

object ConscryptIntegration {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized)
            return

        // initialize Conscrypt
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        val version = Conscrypt.version()
        logger.info("Using Conscrypt/${version.major()}.${version.minor()}.${version.patch()} for TLS")

        val engine = SSLContext.getDefault().createSSLEngine()
        logger.info("Enabled protocols: ${engine.enabledProtocols.joinToString(", ")}")
        logger.info("Enabled ciphers: ${engine.enabledCipherSuites.joinToString(", ")}")

        initialized = true
    }

}