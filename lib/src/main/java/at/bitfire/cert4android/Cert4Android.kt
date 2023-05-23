/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.util.Log
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
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


    // theme

    var theme: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
        MaterialTheme(content = content)
    }


    // notifications

    const val NOTIFICATION_CERT_DECISION = 88809

}