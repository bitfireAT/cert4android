/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CustomCertStoreTest {

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val certStore = CustomCertStore.getInstance(context)

    val testCert = TestCertificates.testCert

    @Before
    fun clearKeys() {
        certStore.clearUserDecisions()
        assertFalse(certStore.userKeyStore.aliases().hasMoreElements())
    }


    @Test
    fun testSetTrustedByUser() {
        // set it to untrustd before to test whether setTrustedByUser removes the cert from untrustedCerts, too
        certStore.setUntrustedByUser(testCert)
        assertEquals(testCert, certStore.untrustedCerts.first())

        // set to trusted, should save to disk
        certStore.setTrustedByUser(testCert)

        assertTrue(certStore.isTrustedByUser(testCert))
        assertTrue(certStore.untrustedCerts.isEmpty())

        // test whether cert was stored to disk:
        // create another cert store to make sure data is loaded from disk again
        val anotherCertStore = CustomCertStore(context)
        assertTrue(anotherCertStore.userKeyStore.aliases().toList().any { alias ->
            anotherCertStore.isTrustedByUser(testCert)
        })
    }

    @Test
    fun testSetUnTrustedByUser() {
        // set to trusted before to test whether setUntrustedByUser removes the cert from trusted key store, too
        certStore.setTrustedByUser(testCert)    // saves trust to disk

        certStore.setUntrustedByUser(testCert)
        assertEquals(testCert, certStore.untrustedCerts.first())

        // test whether now empts key store was saved to disk:
        // create another cert store to make sure data is loaded from disk again
        val anotherCertStore = CustomCertStore(context)
        assertFalse(anotherCertStore.isTrustedByUser(testCert))
    }

}