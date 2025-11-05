package at.bitfire.cert4android

import okhttp3.internal.tls.BasicCertificateChainCleaner
import okhttp3.internal.tls.BasicTrustRootIndex
import org.conscrypt.Conscrypt
import org.junit.Before
import org.junit.Test
import java.security.Security
import java.util.logging.Logger
import javax.net.ssl.SSLContext

class ConscryptIntegrationTest {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    @Before
    fun setUp() {
        // initialize Conscrypt
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        val version = Conscrypt.version()
        logger.info("Using Conscrypt/${version.major()}.${version.minor()}.${version.patch()} for TLS")

        val engine = SSLContext.getDefault().createSSLEngine()
        logger.info("Enabled protocols: ${engine.enabledProtocols.joinToString(", ")}")
        logger.info("Enabled ciphers: ${engine.enabledCipherSuites.joinToString(", ")}")
    }


    /**
     * Test for https://github.com/google/conscrypt/issues/1268.
     *
     * See also https://github.com/bitfireAT/cert4android/pull/48.
     */
    @Test
    fun test_X509Certificate_toString() {
        val testCert = TestCertificates.crashCert()

        // Crashes with Conscrypt 2.5.3
        // Uncomment with Conscrypt >2.5.3
//        System.err.println(testCert.toString())
    }

    @Test
    fun testBasicCertificateChainCleaner() {
        val cleaner = BasicCertificateChainCleaner(BasicTrustRootIndex())

        // See https://github.com/bitfireAT/cert4android/issues/72
        // CRASHES with Conscrypt 2.5.3:
//         cleaner.clean(listOf(TestCertificates.crashCert()), "doesn't matter")

        // This is relevant, because okhttp creates such a BasicCertificateChainManager
        // when using a custom X509TrustManager. However when the trust manager extends
        // X509ExtendedTrustManager, AndroidCertificateChainManager is used on Android.
    }

}