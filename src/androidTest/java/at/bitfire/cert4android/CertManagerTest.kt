package at.bitfire.cert4android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Rule

abstract class CertManagerTest {
    companion object {
        private fun getSiteCertificates(url: URL): List<X509Certificate> {
            val conn = url.openConnection() as HttpsURLConnection
            try {
                conn.inputStream.read()
                val certs = mutableListOf<X509Certificate>()
                conn.serverCertificates.forEach { certs += it as X509Certificate }
                return certs
            } finally {
                conn.disconnect()
            }
        }
    }

    @JvmField
    @Rule
    val serviceTestRule = ServiceTestRule()

    protected val siteCerts: List<X509Certificate> = getSiteCertificates(URL("https://www.davdroid.com"))

    protected fun addCustomCertificate() {
        // add certificate and check again
        val intent = Intent(InstrumentationRegistry.getInstrumentation().context, CustomCertService::class.java)
        intent.action = CustomCertService.CMD_CERTIFICATION_DECISION
        intent.putExtra(CustomCertService.EXTRA_CERTIFICATE, siteCerts!!.first().encoded)
        intent.putExtra(CustomCertService.EXTRA_TRUSTED, true)
        startService(intent, CustomCertService::class.java)
    }

    protected fun bindService(clazz: Class<out Service>): IBinder {
        var binder = serviceTestRule.bindService(Intent(InstrumentationRegistry.getInstrumentation().targetContext, clazz))
        var it = 0
        while (binder == null && it++ <100) {
            binder = serviceTestRule.bindService(Intent(InstrumentationRegistry.getInstrumentation().targetContext, clazz))
            System.err.println("Waiting for ServiceTestRule.bindService")
            Thread.sleep(50)
        }
        if (binder == null)
            throw IllegalStateException("Couldn't bind to service")
        return binder
    }

    protected fun startService(intent: Intent, clazz: Class<out Service>) {
        serviceTestRule.startService(intent)
        bindService(clazz)
    }
}
