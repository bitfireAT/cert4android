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

import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CustomCertManagerTest {

    private lateinit var certStore: CertStore
    private lateinit var certManager: CustomCertManager
    private lateinit var paranoidCertManager: CustomCertManager

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
        certManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }

    @Test(expected = CertificateException::class)
    fun testParanoidCertificate() {
        paranoidCertManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }

    @Test
    fun testAddCustomCertificate() {
        addTrustedCertificate()
        paranoidCertManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }

    @Test(expected = CertificateException::class)
    fun testRemoveCustomCertificate() {
        addTrustedCertificate()

        // remove certificate again
        // should now be rejected for the whole session
        addUntrustedCertificate()

        paranoidCertManager.checkServerTrusted(siteCerts.toTypedArray(), "RSA")
    }


    // helpers

    private fun addTrustedCertificate() {
        certStore.setTrustedByUser(siteCerts.first())
    }

    private fun addUntrustedCertificate() {
        certStore.setUntrustedByUser(siteCerts.first())
    }

    companion object {
        private lateinit var siteCerts: List<X509Certificate>

        @JvmStatic
        @BeforeClass
        fun setUp() {
            siteCerts = try {
                getSiteCertificates(URL("https://www.davx5.com"))
            } catch (_: IOException) {
                // Skip all tests if the certs can't be fetched
                throw AssumptionViolatedException("Couldn't load certificate from Web")
            }
        }

        fun getSiteCertificates(url: URL): List<X509Certificate> {
            val conn = url.openConnection() as HttpsURLConnection
            try {
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
                conn.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                    init(
                        null,
                        arrayOf<TrustManager>(object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                        }),
                        SecureRandom()
                    )
                }.socketFactory
                conn.inputStream.use { stream ->
                    stream.read()
                    val certs = mutableListOf<X509Certificate>()
                    conn.serverCertificates.forEach { certs += it as X509Certificate }
                    return certs
                }
            } finally {
                conn.disconnect()
            }
        }
    }

}