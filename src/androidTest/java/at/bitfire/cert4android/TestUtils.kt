package at.bitfire.cert4android

import android.content.Context
import androidx.test.core.app.ApplicationProvider

val Any.context: Context by lazy {
    ApplicationProvider.getApplicationContext()
}
