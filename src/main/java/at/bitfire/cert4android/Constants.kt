/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android

import java.util.logging.Logger

class Constants {
    companion object {

        @JvmField
        var log: Logger = Logger.getLogger("cert4android")

        @JvmField
        val NOTIFICATION_CERT_DECISION = 88809

    }
}
