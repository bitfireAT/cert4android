package at.bitfire.cert4android.demo

import android.app.Application
import android.net.SSLCertificateSocketFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : ComponentActivity() {

    private val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Column {
                    Button(onClick = {
                        model.testAccess(trustSystemCerts = true)
                    }) {
                        Text("Access URL with trusted system certs")
                    }

                    Button(onClick = {
                        model.testAccess(trustSystemCerts = false)
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with distrusted system certs")
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

        fun testAccess(trustSystemCerts: Boolean) = viewModelScope.launch(Dispatchers.IO) {
            try {
                val urlConn = URL("https://www.github.com").openConnection() as HttpsURLConnection

                // set cert4android TrustManager and HostnameVerifier
                val certMgr = CustomCertManager(
                    getApplication(),
                    trustSystemCerts = trustSystemCerts,
                    interactive = true,
                    appInForeground = appInForeground
                )
                urlConn.hostnameVerifier = CustomHostnameVerifier(getApplication(), BrowserCompatHostnameVerifier())
                urlConn.sslSocketFactory = object : SSLCertificateSocketFactory(1000) {
                    init {
                        setTrustManagers(arrayOf(certMgr))
                    }
                }

                // access sample URL
                Log.i(Cert4Android.TAG, "testAccess(): HTTP ${urlConn.responseCode}")
                resultMessage.postValue("${urlConn.responseCode} ${urlConn.responseMessage}")
                urlConn.inputStream.close()
            } catch (e: Exception) {
                resultMessage.postValue("ERROR: ${e.message}")
            }
        }

    }

}