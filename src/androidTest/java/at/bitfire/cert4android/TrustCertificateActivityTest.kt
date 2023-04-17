package at.bitfire.cert4android

import android.content.Intent
import android.util.Log
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import at.bitfire.cert4android.TrustCertificateActivity.Companion.TEST_TAG_ACCEPT
import at.bitfire.cert4android.TrustCertificateActivity.Companion.TEST_TAG_CHECKBOX
import at.bitfire.cert4android.TrustCertificateActivity.Companion.TEST_TAG_REJECT
import java.security.cert.CertificateException
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TrustCertificateActivityTest: CertManagerTest() {

    private lateinit var scenario: ActivityScenario<TrustCertificateActivity>

    private lateinit var certManager: CustomCertManager

    @get:Rule
    val composeTestRule = createAndroidIntentComposeRule<TrustCertificateActivity> {
        Intent(it, TrustCertificateActivity::class.java).apply {
            val cert = siteCerts.first()
            putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, cert.encoded)
        }
    }

    @Before
    fun initCertManager() {
        // prepare a bound and ready service for testing
        // loop required because of https://code.google.com/p/android/issues/detail?id=180396
        val binder = bindService(CustomCertService::class.java)
        Assert.assertNotNull(binder)

        CustomCertManager.resetCertificates(context)

        certManager = CustomCertManager(context, false)
        Assert.assertNotNull(certManager)
    }

    @After
    fun closeCertManager() {
        certManager.close()
    }

    @Test
    fun test_launch_withExtras_accept() {
        val siteCert = siteCerts.first()

        // First make sure that the certificate has not been trusted
        assertThrows(CertificateException::class.java) {
            certManager.checkCustomTrusted(siteCert)
        }

        // Launch the activity with the given certificate
        val intent = Intent(context, TrustCertificateActivity::class.java)
            .putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, siteCert.encoded)
        scenario = launchActivity(intent)
        // Make sure the activity has been launched
        assertEquals(Lifecycle.State.RESUMED, scenario.state)

        // Make sure the accept button is disabled by default
        composeTestRule
            .onNodeWithTag(TEST_TAG_ACCEPT)
            .assertIsNotEnabled()

        // Mark the checkbox
        composeTestRule
            .onNodeWithTag(TEST_TAG_CHECKBOX)
            .performClick()

        // Make sure the accept button is now enabled
        composeTestRule
            .onNodeWithTag(TEST_TAG_CHECKBOX)
            .assertIsEnabled()

        // Press accept
        composeTestRule
            .onNodeWithTag(TEST_TAG_ACCEPT)
            .performClick()

        // Wait until the activity is finished
        composeTestRule.waitUntil(10_000) {
            Log.d("TrustCertTest", "State: ${scenario.state}")
            scenario.state == Lifecycle.State.DESTROYED
        }

        // Check that the certificate is now trusted
        certManager.checkCustomTrusted(siteCert)
    }

    @Test
    fun test_launch_withExtras_reject() {
        // First make sure that the certificate has not been trusted
        assertThrows(CertificateException::class.java) {
            certManager.checkCustomTrusted(siteCerts.first())
        }

        // Launch the activity with the given certificate
        val cert = siteCerts.first()
        val intent = Intent(context, TrustCertificateActivity::class.java)
            .putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, cert.encoded)
        scenario = launchActivity(intent)
        // Make sure the activity has been launched
        assertEquals(Lifecycle.State.RESUMED, scenario.state)

        // Press reject
        composeTestRule
            .onNodeWithTag(TEST_TAG_REJECT)
            .performClick()

        // Wait until the activity is finished
        composeTestRule.waitUntil(10_000) { scenario.state == Lifecycle.State.DESTROYED }

        // Check that the certificate is still not trusted
        assertThrows(CertificateException::class.java) {
            certManager.checkCustomTrusted(siteCerts.first())
        }
    }

}