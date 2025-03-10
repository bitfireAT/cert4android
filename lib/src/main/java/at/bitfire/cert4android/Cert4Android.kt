/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.util.Log
import java.util.logging.Level
import java.util.logging.Logger

object Cert4Android {

    // logging

    const val TAG = "cert4android"

    var log: Logger = Logger.getLogger(TAG)
    init {
        log.level = if (Log.isLoggable(TAG, Log.VERBOSE))
            Level.ALL
        else
            Level.INFO
    }

}