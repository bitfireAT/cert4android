/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.cert4android

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.tls.OkHostnameVerifier
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLContext

class OkhttpTest {

    @Before
    fun setUp() {
        // initialize Conscrypt
        ConscryptIntegration.initialize()
    }

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

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