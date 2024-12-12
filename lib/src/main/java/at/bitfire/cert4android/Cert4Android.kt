/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        MaterialTheme {
            Box(Modifier.safeDrawingPadding()) {
                content()
            }
        }
    }

}