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
import java.security.cert.X509Certificate
import java.util.logging.Level
import java.util.logging.Logger

class TestCertStore(): CertStore {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /** custom TrustStore (simple map) */
    @VisibleForTesting
    internal val userKeyStore = mutableMapOf<String, X509Certificate>()

    /** in-memory store for untrusted certs */
    @VisibleForTesting
    internal var untrustedCerts = HashSet<X509Certificate>()

    @Synchronized
    override fun clearUserDecisions() {
        logger.info("Clearing user-(dis)trusted certificates")

        for (alias in userKeyStore.keys)
            userKeyStore.remove(alias)

        // clear untrusted certs
        untrustedCerts.clear()
    }

    /**
     * Determines whether a certificate chain is trusted.
     */
    override fun isTrusted(chain: Array<X509Certificate>, authType: String, trustSystemCerts: Boolean, appInForeground: StateFlow<Boolean>?): Boolean {
        if (chain.isEmpty())
            throw IllegalArgumentException("Certificate chain must not be empty")
        val cert = chain[0]

        synchronized(this) {
            // explicitly accepted by user?
            if (isTrustedByUser(cert))
                return true

            // explicitly rejected by user?
            if (untrustedCerts.contains(cert))
                return false

            // trusted by system? (if applicable)
            if (trustSystemCerts)
                return true // system trusts all certificates
        }
        logger.log(Level.INFO, "Certificate not known and running in non-interactive mode, rejecting")
        return false
    }

    /**
     * Determines whether a certificate has been explicitly accepted by the user. In this case,
     * we can ignore an invalid host name for that certificate.
     */
    @Synchronized
    override fun isTrustedByUser(cert: X509Certificate): Boolean =
        userKeyStore.containsValue(cert)

    @Synchronized
    override fun setTrustedByUser(cert: X509Certificate) {
        val alias = CertUtils.getTag(cert)
        logger.info("Trusted by user: ${cert.subjectDN.name} ($alias)")
        userKeyStore[alias] = cert
        untrustedCerts -= cert
    }

    @Synchronized
    override fun setUntrustedByUser(cert: X509Certificate) {
        logger.info("Distrusted by user: ${cert.subjectDN.name}")

        // find certificate
        val alias = userKeyStore.entries.find { it.value == cert }?.key
        if (alias != null)
        // and delete, if applicable
            userKeyStore.remove(alias)
        untrustedCerts += cert
    }

}