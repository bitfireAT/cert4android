package at.bitfire.cert4android

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

val Any.context: Context by lazy {
    ApplicationProvider.getApplicationContext()
}

fun <T: ComponentActivity> ActivityScenario<T>.awaitUntil(targetState: Lifecycle.State) {
    val latch = CountDownLatch(1)
    onActivity {
        it.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                if (targetState == Lifecycle.State.CREATED) latch.countDown()
            }

            override fun onResume(owner: LifecycleOwner) {
                if (targetState == Lifecycle.State.RESUMED) latch.countDown()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (targetState == Lifecycle.State.STARTED) latch.countDown()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (targetState == Lifecycle.State.DESTROYED) latch.countDown()
            }
        })
    }
    Assert.assertTrue(latch.await(10, TimeUnit.SECONDS))
}
