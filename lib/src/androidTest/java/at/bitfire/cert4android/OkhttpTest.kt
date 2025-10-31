package at.bitfire.cert4android

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.tls.BasicCertificateChainCleaner
import okhttp3.internal.tls.BasicTrustRootIndex
import okhttp3.internal.tls.OkHostnameVerifier
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.net.ssl.SSLContext

class OkhttpTest {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    init {
        ConscryptIntegration.initialize()
    }

    @Test
    fun testAccessICloudComWithCache() {
        // See https://github.com/bitfireAT/davx5/issues/713 and
        // https://github.com/bitfireAT/cert4android/issues/72

        val client = buildClient(
            useCache = false    // CRASHES when true!
        )

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

    @Test
    fun testBasicCertificateChainCleaner() {
        val cleaner = BasicCertificateChainCleaner(BasicTrustRootIndex())

        // See https://github.com/bitfireAT/cert4android/issues/72
        // CRASHES with Conscrypt 2.5.3:
        // cleaner.clean(listOf(TestCertificates.crashCert()), "doesn't matter")

        // This is relevant, because okhttp creates such a BasicCertificateChainManager
        // when using a custom X509TrustManager. However when the trust manager extends
        // X509ExtendedTrustManager, AndroidCertificateChainManager is used on Android.
    }


    fun buildClient(useCache: Boolean): OkHttpClient {
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

        if (useCache)
            builder.cache(Cache(context.cacheDir, 10 * 1024 * 1024))

        return builder.build()
    }

}