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

import org.conscrypt.Conscrypt
import java.security.Security
import java.util.logging.Logger
import javax.net.ssl.SSLContext

object ConscryptIntegration {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized)
            return

        // initialize Conscrypt
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        val version = Conscrypt.version()
        logger.info("Using Conscrypt/${version.major()}.${version.minor()}.${version.patch()} for TLS")

        val engine = SSLContext.getDefault().createSSLEngine()
        logger.info("Enabled protocols: ${engine.enabledProtocols.joinToString(", ")}")
        logger.info("Enabled ciphers: ${engine.enabledCipherSuites.joinToString(", ")}")

        initialized = true
    }

}