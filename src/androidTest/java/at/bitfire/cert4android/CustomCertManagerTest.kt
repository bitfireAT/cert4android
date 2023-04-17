/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.ServiceTestRule
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection

class CustomCertManagerTest: CertManagerTest() {

    lateinit var certManager: CustomCertManager
    lateinit var paranoidCertManager: CustomCertManager

    init {
        CustomCertManager.SERVICE_TIMEOUT = 1000
    }

    @Before
    fun initCertManager() {
        // prepare a bound and ready service for testing
        // loop required because of https://code.google.com/p/android/issues/detail?id=180396
        val binder = bindService(CustomCertService::class.java)
        assertNotNull(binder)

        val context = getInstrumentation().context
        CustomCertManager.resetCertificates(context)

        certManager = CustomCertManager(context, false)
        assertNotNull(certManager)

        paranoidCertManager = CustomCertManager(context, false, false)
        assertNotNull(paranoidCertManager)
    }

    @After
    fun closeCertManager() {
        paranoidCertManager.close()
        certManager.close()
    }


    @Test(expected = CertificateException::class)
    fun testCheckClientCertificate() {
        certManager.checkClientTrusted(null, null)
    }

    @Test
    fun testTrustedCertificate() {
        certManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }

    @Test(expected = CertificateException::class)
    fun testParanoidCertificate() {
        paranoidCertManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }

    @Test
    fun testAddCustomCertificate() {
        addCustomCertificate()
        paranoidCertManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }

    // fails randomly for unknown reason:
    @Test(expected = CertificateException::class)
    fun testRemoveCustomCertificate() {
        addCustomCertificate()

        // remove certificate and check again
        // should now be rejected for the whole session, i.e. no timeout anymore
        val intent = Intent(getInstrumentation().context, CustomCertService::class.java)
        intent.action = CustomCertService.CMD_CERTIFICATION_DECISION
        intent.putExtra(CustomCertService.EXTRA_CERTIFICATE, siteCerts.first().encoded)
        intent.putExtra(CustomCertService.EXTRA_TRUSTED, false)
        startService(intent, CustomCertService::class.java)
        paranoidCertManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }

}
