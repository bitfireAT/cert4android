package at.bitfire.cert4android

import okhttp3.internal.tls.BasicCertificateChainCleaner
import okhttp3.internal.tls.BasicTrustRootIndex
import org.junit.Before
import org.junit.Test

class ConscryptIntegrationTest {

    @Before
    fun setUp() {
        // initialize Conscrypt
        ConscryptIntegration.initialize()
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