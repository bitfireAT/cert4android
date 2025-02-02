/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class CustomCertManagerTest {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    private lateinit var certManager: CustomCertManager
    private lateinit var paranoidCertManager: CustomCertManager

    private var siteCerts: List<X509Certificate>? =
        try {
            TestCertificates.getSiteCertificates(URL("https://www.davx5.com"))
        } catch(_: IOException) {
            null
        }
    init {
        assumeNotNull("Couldn't load certificate from Web", siteCerts)
    }

    @Before
    fun createCertManager() {
        certManager = CustomCertManager(context, true, null)
        paranoidCertManager = CustomCertManager(context, false, null)
    }


    @Test(expected = CertificateException::class)
    fun testCheckClientCertificate() {
        certManager.checkClientTrusted(null, null)
    }

    @Test
    fun testTrustedCertificate() {
        certManager.checkServerTrusted(siteCerts!!.toTypedArray(), "RSA")
    }

    @Test(expected = CertificateException::class)
    fun testParanoidCertificate() {
        paranoidCertManager.checkServerTrusted(siteCerts!!.toTypedArray(), "RSA")
    }

    @Test
    fun testAddCustomCertificate() {
        addTrustedCertificate()
        paranoidCertManager.checkServerTrusted(siteCerts!!.toTypedArray(), "RSA")
    }

    @Test(expected = CertificateException::class)
    fun testRemoveCustomCertificate() {
        addTrustedCertificate()

        // remove certificate again
        // should now be rejected for the whole session
        addUntrustedCertificate()

        paranoidCertManager.checkServerTrusted(siteCerts!!.toTypedArray(), "RSA")
    }


    // helpers

    private fun addTrustedCertificate() {
        CustomCertStore.getInstance(context).setTrustedByUser(siteCerts!!.first())
    }

    private fun addUntrustedCertificate() {
        CustomCertStore.getInstance(context).setUntrustedByUser(siteCerts!!.first())
    }

}