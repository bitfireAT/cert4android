package at.bitfire.cert4android

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.tls.OkHostnameVerifier
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.net.ssl.SSLContext

class OkhttpTest {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    @Test
    fun testAccessICloudComWithCache() {
        val client = buildClient()

        // access sample URL
        val call = client.newCall(
            Request.Builder()
                .get()
                .cacheControl(CacheControl.FORCE_NETWORK)   // don't retrieve from cache, the problem is storing to cache
                .url("https://icloud.com")
                .build()
        )
        call.execute().use { response ->
            assertEquals(200, response.code)
        }
    }


    fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()

        // set cert4android TrustManager and HostnameVerifier
        val certManager = CustomCertManager(
            context,
            trustSystemCerts = true,
            appInForeground = null
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            /* km = */ null,
            /* tm = */ arrayOf(certManager),
            /* random = */ null
        )
        builder
            .sslSocketFactory(sslContext.socketFactory, certManager)
            .hostnameVerifier(certManager.HostnameVerifier(OkHostnameVerifier))

        // add cache
        builder.cache(Cache(context.cacheDir, 10 * 1024 * 1024))

        return builder.build()
    }

}