package at.bitfire.cert4android.demo

import android.annotation.SuppressLint
import android.app.Application
import android.net.SSLCertificateSocketFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import at.bitfire.cert4android.Cert4Android
import at.bitfire.cert4android.TrustCertificateDialog
import at.bitfire.cert4android.CertificateDetails
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.cert4android.CustomCertStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.apache.http.conn.ssl.AllowAllHostnameVerifier
import org.apache.http.conn.ssl.StrictHostnameVerifier
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Example implementation for testing and to showcase usage of [CustomCertManager].
 */
class MainActivity : ComponentActivity() {

    private val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snackBarHostState = remember { SnackbarHostState() }
            val certificateDetails = model.certificateDetailsFlow.collectAsStateWithLifecycle().value

            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Button(onClick = {
                        model.testAccess("https://www.github.com")
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access normal URL with trusted system certs")
                    }

                    Button(onClick = {
                        model.testAccess("https://www.github.com", trustSystemCerts = false)
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access normal URL with distrusted system certs")
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
                        model.testAccess(
                            "https://wrong.host.badssl.com/",
                            trustSystemCerts = false
                        )
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Access URL with certificate for wrong host name with distrusted system certs")
                    }

                    Button(onClick = {
                        model.reset()
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Clear trusted certs")
                    }

                    val result = model.resultMessage.observeAsState()
                    result.value?.let { msg ->
                        if (msg.isNotEmpty())
                            LaunchedEffect(snackBarHostState) {
                                snackBarHostState.showSnackbar(msg)
                                model.resultMessage.value = null
                            }
                    }
                }

                if (certificateDetails != null)
                    TrustCertificateDialog(certificateDetails, model::registerUserDecision)

                SnackbarHost(
                    snackBarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }


    class Model(application: Application) : AndroidViewModel(application) {

        val resultMessage = MutableLiveData<String>()

        private val _certificateDetailsFlow = MutableStateFlow<CertificateDetails?>(null)
        val certificateDetailsFlow: StateFlow<CertificateDetails?> = _certificateDetailsFlow

        @Volatile
        private var userDecision: CompletableDeferred<Boolean> = CompletableDeferred()

        fun registerUserDecision(decision: Boolean) {
            userDecision.complete(decision)
            _certificateDetailsFlow.value = null
        }


        init {
            // The default HostnameVerifier is called before our per-connection HostnameVerifier.
            @SuppressLint("AllowAllHostnameVerifier")
            HttpsURLConnection.setDefaultHostnameVerifier(AllowAllHostnameVerifier())
        }

        fun reset() = viewModelScope.launch(Dispatchers.IO) {
            CustomCertStore.getInstance(getApplication()).clearUserDecisions()
        }

        fun testAccess(url: String, trustSystemCerts: Boolean = true) =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val urlConn = URL(url).openConnection() as HttpsURLConnection

                    // set cert4android TrustManager and HostnameVerifier
                    val certMgr = CustomCertManager(
                        getApplication(),
                        trustSystemCerts = trustSystemCerts,
                        viewModelScope,
                        getUserDecision = { cert ->
                            // Reset user decision
                            userDecision = CompletableDeferred()

                            // Show TrustDecisionDialog with certificate details to user
                            _certificateDetailsFlow.value = CertificateDetails.fromX509(cert)

                            // Wait for user decision and return it
                            userDecision.await()
                        }
                    )
                    urlConn.hostnameVerifier = certMgr.HostnameVerifier(StrictHostnameVerifier())
                    urlConn.sslSocketFactory =
                        object : SSLCertificateSocketFactory(/* handshakeTimeoutMillis = */ 1000) {
                            init {
                                setTrustManagers(arrayOf(certMgr))
                            }
                        }

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