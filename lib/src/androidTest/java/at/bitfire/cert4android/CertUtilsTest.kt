package at.bitfire.cert4android

import org.junit.Assert.assertEquals
import org.junit.Test

class CertUtilsTest {

    @Test
    fun test_GetTag() {
        assertEquals(
            "D222F403C591812720257368EA1C1F61AB60023774B449D589B26D6B43CC2DFE78212B4B9D82B5CD41107678677BEED3C0637F1CF331DE5B023DBA985B46FF18",
            CertUtils.getTag(TestCertificates.testCert)
        )
    }

}