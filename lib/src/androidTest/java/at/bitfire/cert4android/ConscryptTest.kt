package at.bitfire.cert4android

import org.junit.Test
import java.net.URL

class ConscryptTest {

    @Test
    fun test_X509Certificate_toString() {
        val certs = TestCertificates.getSiteCertificates(URL("https://expired.badssl.com"))

        // Crashes with Conscrypt 2.5.3
        for (cert in certs)
            System.err.println(cert.toString())
    }

}