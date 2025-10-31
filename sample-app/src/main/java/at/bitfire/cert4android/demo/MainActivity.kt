package at.bitfire.cert4android.demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.cert4android.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.tls.OkHostnameVerifier
import org.apache.http.conn.ssl.AllowAllHostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

class MainActivity : ComponentActivity() {

    private val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        setContent {
            ThemeManager.theme {
                Column(Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())) {
                    Row {
                        Checkbox(model.appInForeground.collectAsState().value, onCheckedChange = { foreground ->
                            model.setInForeground(foreground)
                        })
                        Text("App in foreground")
                    }

                    Button(onClick = {
                        model.testAccess("https://icloud.com")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access icloud.com with trusted system certs")
                    }

                    Button(onClick = {
                        model.testAccess("https://icloud.com", trustSystemCerts = false)
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access icloud.com with distrusted system certs")
                    }

                    Button(onClick = {
                        model.testAccess("https://expired.badssl.com/")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with expired certificate")
                    }

                    Button(onClick = {
                        model.testAccess("https://self-signed.badssl.com/")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with self-signed certificate")
                    }

                    Button(onClick = {
                        model.testAccess("https://wrong.host.badssl.com/")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with certificate for wrong host name")
                    }

                    Button(onClick = {
                        model.testAccess("https://wrong.host.badssl.com/", trustSystemCerts = false)
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with certificate for wrong host name with distrusted system certs")
                    }

                    Button(onClick = {
                        model.reset()
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Clear trusted certs")
                    }

                    val snackBarHostState = remember { SnackbarHostState() }
                    val result = model.resultMessage.observeAsState()
                    result.value?.let { msg ->
                        if (msg.isNotEmpty())
                            LaunchedEffect(snackBarHostState) {
                                snackBarHostState.showSnackbar(msg)
                                model.resultMessage.value = null
                            }
                    }
                    SnackbarHost(snackBarHostState)
                }
            }
        }
    }


    class Model(application: Application): AndroidViewModel(application) {

        val context: Context
            get() = getApplication()

        val appInForeground = MutableStateFlow(true)
        val resultMessage = MutableLiveData<String>()

        init {
            // The default HostnameVerifier is called before our per-connection HostnameVerifier.
            @SuppressLint("AllowAllHostnameVerifier")
            HttpsURLConnection.setDefaultHostnameVerifier(AllowAllHostnameVerifier())
        }

        fun reset() = viewModelScope.launch(Dispatchers.IO) {
            CustomCertStore.getInstance(context).clearUserDecisions()
        }

        fun setInForeground(foreground: Boolean) {
            appInForeground.value = foreground
        }

        fun testAccess(url: String, trustSystemCerts: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = buildClient(trustSystemCerts)

                // access sample URL
                val call = client.newCall(
                    Request.Builder()
                        .get()
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .url(url)
                        .build()
                )
                call.execute().use { response ->
                    // log result
                    Log.i(TAG, "testAccess(): HTTP ${response.code}")
                    resultMessage.postValue("${response.code} ${response.message}")
                }
            } catch (e: Exception) {
                resultMessage.postValue("testAccess() ERROR: ${e.message}")
                Log.w(TAG, "testAccess(): ERROR: ${e.message}")
            }
        }

        fun buildClient(trustSystemCerts: Boolean): OkHttpClient {
            val builder = OkHttpClient.Builder()

            // set cert4android TrustManager and HostnameVerifier
            val certManager = CustomCertManager(
                context,
                trustSystemCerts = trustSystemCerts,
                appInForeground = appInForeground
            )

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(
                /* km = */ null,
                /* tm = */ arrayOf(certManager),
                /* random = */ null
            )
            builder
                .sslSocketFactory(sslContext.socketFactory, certManager)
                .hostnameVerifier(certManager.HostnameVerifier(OkHostnameVerifier))

            // add cache
            //builder.cache(Cache(context.cacheDir, 10 * 1024 * 1024))

            return builder.build()
        }

    }


    companion object {
        const val TAG = "cert4android.sample-app"
    }

}