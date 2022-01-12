/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {

    const val CHANNEL_CERTIFICATES = "cert4android"

    fun createChannels(context: Context): NotificationManagerCompat {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CERTIFICATES,
                    context.getString(R.string.certificate_notification_connection_security), NotificationManager.IMPORTANCE_DEFAULT))

        return NotificationManagerCompat.from(context)
    }

}