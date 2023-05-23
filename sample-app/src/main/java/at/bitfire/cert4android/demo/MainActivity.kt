package at.bitfire.cert4android.demo

import android.app.Application
import android.net.SSLCertificateSocketFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.cert4android.Constants
import at.bitfire.cert4android.CustomCertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

class MainActivity : ComponentActivity() {

    private val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Button(onClick = {
                    model.accessDistrustedSystemCert()
                }) {
                    Text("Access URL with distrusted system certs")
                }
            }
        }
    }


    class Model(application: Application): AndroidViewModel(application) {

        fun accessDistrustedSystemCert() = viewModelScope.launch(Dispatchers.IO) {
            val urlConn = URL("https://google.com").openConnection() as HttpsURLConnection

            // set cert4android TrustManager and HostnameVerifier
            val certMgr = CustomCertManager(getApplication(), interactive = true, trustSystemCerts = false, appInForeground = true)
            urlConn.hostnameVerifier = certMgr.hostnameVerifier(BrowserCompatHostnameVerifier())
            urlConn.sslSocketFactory = object : SSLCertificateSocketFactory(1000) {
                init {
                    setTrustManagers(arrayOf(certMgr))
                }
            }

            // access sample URL
            Log.i(Constants.TAG, "accessDistrustedSystemCert(): HTTP ${urlConn.responseCode}")
            urlConn.inputStream.close()
        }

    }

}