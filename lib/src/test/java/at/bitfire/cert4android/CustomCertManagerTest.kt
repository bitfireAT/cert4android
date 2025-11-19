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

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CustomCertManagerTest {

    private lateinit var certStore: CertStore
    private lateinit var certManager: CustomCertManager
    private lateinit var paranoidCertManager: CustomCertManager

    private var siteCerts: List<X509Certificate>? =
        try {
            getSiteCertificates(URL("https://www.davx5.com"))
        } catch(_: IOException) {
            null
        }
    init {
        assumeNotNull("Couldn't load certificate from Web", siteCerts)
    }

    @Before
    fun createCertManager() {
        certStore = TestCertStore()
        certManager = CustomCertManager(certStore, true, null)
        paranoidCertManager = CustomCertManager(certStore, false, null)
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
        certStore.setTrustedByUser(siteCerts!!.first())
    }

    private fun addUntrustedCertificate() {
        certStore.setUntrustedByUser(siteCerts!!.first())
    }

    /**
     * Get the certificates of a site (bypassing all trusted checks).
     *
     * @param url the URL to get the certificates from
     * @return the certificates of the site
     */
    fun getSiteCertificates(url: URL): List<X509Certificate> {
        val port = if (url.port != -1) url.port else 443
        val host = url.host

        // Create a TrustManager which accepts all certificates
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        // Create an SSLContext using the trust-all manager
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }

        // Create an SSL socket and force a TLS handshake
        // (HttpsURLConnection performs the handshake lazily and sometimes the handshake is not
        // executed before this method gets called)
        sslContext.socketFactory.createSocket(host, port).use { socket ->
            val sslSocket = socket as SSLSocket
            // Explicitly start the handshake (gets certificate)
            sslSocket.startHandshake()
            // server certificates now available in SSLSession
            return sslSocket.session.peerCertificates.map { it as X509Certificate }
        }
    }

}