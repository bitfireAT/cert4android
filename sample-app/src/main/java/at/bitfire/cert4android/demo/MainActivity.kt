package at.bitfire.cert4android.demo

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.SSLCertificateSocketFactory
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
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
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
import at.bitfire.cert4android.Cert4Android
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.cert4android.CustomHostnameVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier
import org.apache.http.conn.ssl.StrictHostnameVerifier
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : ComponentActivity() {

    private val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        setContent {
            MaterialTheme {
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
                        model.testAccess()
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access normal URL with trusted system certs")
                    }

                    Button(onClick = {
                        model.testAccess(trustSystemCerts = false)
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access normal URL with distrusted system certs")
                    }

                    Button(onClick = {
                        model.testAccess(url = "https://expired.badssl.com/")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with expired certificate")
                    }

                    Button(onClick = {
                        model.testAccess(url = "https://self-signed.badssl.com/")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with self-signed certificate")
                    }

                    Button(onClick = {
                        model.testAccess(url = "https://wrong.host.badssl.com/")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with certificate for wrong host name")
                    }

                    Button(onClick = {
                        model.testAccess(url = "https://wrong.host.badssl.com/", trustSystemCerts = false)
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

        val appInForeground = MutableStateFlow(true)
        val resultMessage = MutableLiveData<String>()

        fun reset() = viewModelScope.launch(Dispatchers.IO) {
            CustomCertStore.getInstance(getApplication()).clearUserDecisions()
        }

        fun setInForeground(foreground: Boolean) {
            appInForeground.value = foreground
        }

        fun testAccess(url: String = "https://www.github.com", trustSystemCerts: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {
            try {
                val urlConn = URL(url).openConnection() as HttpsURLConnection

                // set cert4android TrustManager and HostnameVerifier
                val certMgr = CustomCertManager(
                    getApplication(),
                    trustSystemCerts = trustSystemCerts,
                    appInForeground = appInForeground
                )
                urlConn.sslSocketFactory = object : SSLCertificateSocketFactory(1000) {
                    init {
                        setTrustManagers(arrayOf(certMgr))
                    }
                }
                urlConn.hostnameVerifier = certMgr.hostnameVerifier(StrictHostnameVerifier())

                // access sample URL
                Log.i(Cert4Android.TAG, "testAccess(): HTTP ${urlConn.responseCode}")
                resultMessage.postValue("${urlConn.responseCode} ${urlConn.responseMessage}")
                urlConn.inputStream.close()
            } catch (e: Exception) {
                resultMessage.postValue("testAccess() ERROR: ${e.message}")
                Log.w(Cert4Android.TAG, "testAccess(): ERROR: ${e.message}")
            }
        }

    }

}