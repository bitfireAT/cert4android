/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object ThemeManager {

    // theme

    var theme: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
        MaterialTheme {
            Box(Modifier.safeDrawingPadding()) {
                content()
            }
        }
    }

}