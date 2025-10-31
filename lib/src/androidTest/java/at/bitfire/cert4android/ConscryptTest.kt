package at.bitfire.cert4android

import org.junit.Before
import org.junit.Test

class ConscryptTest {

    @Before
    fun setUp() {
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
        // System.err.println(testCert.toString())
    }

}