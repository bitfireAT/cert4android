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

import org.junit.Before
import org.junit.Test
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection

class CustomCertManagerTest {

    private val siteCerts: List<X509Certificate> by lazy {
        getSiteCertificates(URL("https://www.davx5.com"))
    }
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

    fun getSiteCertificates(url: URL): List<X509Certificate> {
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.use {
                return conn.serverCertificates.filterIsInstance<X509Certificate>()
            }
        } finally {
            conn.disconnect()
        }
    }

}