/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec

class CertUtilsTest {

    private val certFactory = CertificateFactory.getInstance("X.509")!!


    @Test
    fun testGetTrustManagerSystem() {
        assertNotNull(CertUtils.getTrustManager(null))
    }

    @Test
    fun testFingerprint() {
        javaClass.classLoader!!.getResourceAsStream("davdroid-web.crt").use { stream ->
            val cert = certFactory.generateCertificate(stream) as X509Certificate
            assertEquals("8D:E5:74:B2:AA:3E:5C:EE:62:84:4A:3B:78:71:B6:C3", CertUtils.fingerprint(cert, "MD5"))
            assertEquals("6C:83:A0:12:1A:F5:55:BF:C2:BC:23:DA:78:E4:5F:88:6E:01:0A:BC", CertUtils.fingerprint(cert, MGF1ParameterSpec.SHA1.digestAlgorithm))
        }
    }

    @Test
    fun testGetTag() {
        javaClass.classLoader!!.getResourceAsStream("davdroid-web.crt").use { stream ->
            val cert = certFactory.generateCertificate(stream) as X509Certificate
            assertNotNull(cert)

            assertEquals(
                "4F91DE498B026C600389A9F40D37CBC6A74DBCA060CA0F927556A88B67CC6C8B15D2F7B93B3FA1407AD49994CB3DEA6FA72851A5B680B283E449CEB25B6559A2",
                CertUtils.getTag(cert)
            )
        }
    }

    @Test
    fun testHexString() {
        assertEquals("", CertUtils.hexString(ByteArray(0)))
        assertEquals("00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10", CertUtils.hexString(ByteArray(17) { i -> i.toByte() }))
    }

}