/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.observeOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec.SHA1
import java.security.spec.MGF1ParameterSpec.SHA256
import java.text.DateFormat
import java.util.logging.Level

class TrustCertificateActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CERTIFICATE = "certificate"
        const val EXTRA_TRUSTED = "trusted"
    }

    private val model by viewModels<Model> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return Model(application, intent) as T
            }
        }
    }

    private val backPressedCounter = mutableIntStateOf(0)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addOnNewIntentListener { newIntent ->
            model.processIntent(newIntent)
        }
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                backPressedCounter.intValue++
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.decided.collect { decided ->
                    if (decided)
                        // user has decided, close activity
                        finish()
                }
            }
        }

        setContent {
            MainLayout()
        }
    }


    @Composable
    @Preview
    fun MainLayout() {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        backPressedCounter.asIntState().intValue.let { counter ->
            when {
                counter == 0 -> { /* back button not pressed yet */ }
                counter == 1 ->
                    scope.launch {
                        snackbarHostState.showSnackbar(getString(R.string.trust_certificate_press_back_to_reject))
                    }
                else ->
                    model.registerDecision(false)
            }
        }

        Cert4Android.theme {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.padding(16.dp)
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.trust_certificate_unknown_certificate_found),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )

                    CertificateCard()

                    Text(
                        text = stringResource(R.string.trust_certificate_reset_info),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
            }
        }
    }

    @Composable
    fun CertificateCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
            ) {
                val issuedFor by model.issuedFor.collectAsStateWithLifecycle()
                val issuedBy by model.issuedBy.observeAsState("")
                val validFrom by model.validFrom.observeAsState("")
                val validTo by model.validTo.observeAsState("")
                val sha1 by model.sha1.observeAsState("")
                val sha256 by model.sha256.observeAsState("")

                Text(
                    text = stringResource(R.string.trust_certificate_x509_certificate_details),
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                issuedFor?.let {
                    InfoPack(R.string.trust_certificate_issued_for, it)
                }
                InfoPack(
                    R.string.trust_certificate_issued_by,
                    issuedBy
                )
                InfoPack(
                    R.string.trust_certificate_validity_period,
                    stringResource(
                        R.string.trust_certificate_validity_period_value,
                        validFrom,
                        validTo
                    )
                )

                Text(
                    text = stringResource(R.string.trust_certificate_fingerprints).uppercase(),
                    style = MaterialTheme.typography.overline,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
                Text(
                    text = sha1,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 4.dp),
                )
                Text(
                    text = sha256,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 4.dp),
                )

                var fingerprintVerified by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Checkbox(
                        checked = fingerprintVerified,
                        onCheckedChange = { fingerprintVerified = it }
                    )
                    Text(
                        text = stringResource(R.string.trust_certificate_fingerprint_verified),
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 8.dp),
                        style = MaterialTheme.typography.body2
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    TextButton(
                        enabled = fingerprintVerified,
                        onClick = {
                            model.registerDecision(true)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) { Text(stringResource(R.string.trust_certificate_accept).uppercase()) }
                    TextButton(
                        onClick = {
                            model.registerDecision(false)
                        },
                        modifier = Modifier
                            .weight(1f)
                    ) { Text(stringResource(R.string.trust_certificate_reject).uppercase()) }
                }
            }
        }
    }

    @Composable
    fun InfoPack(@StringRes labelStringRes: Int, text: String) {
        Text(
            text = stringResource(labelStringRes).uppercase(),
            style = MaterialTheme.typography.overline,
            modifier = Modifier
                .fillMaxWidth(),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.body1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }


    class Model(
        application: Application,
        initialIntent: Intent
    ) : AndroidViewModel(application) {

        private var cert: X509Certificate? = null
        val decided = MutableStateFlow(false)

        val issuedFor = MutableStateFlow<String?>(null)
        val issuedBy = MutableLiveData<String>()

        val validFrom = MutableLiveData<String>()
        val validTo = MutableLiveData<String>()

        val sha1 = MutableLiveData<String>()
        val sha256 = MutableLiveData<String>()

        init {
            processIntent(initialIntent)
        }

        fun processIntent(intent: Intent) = viewModelScope.launch(Dispatchers.Default) {
            // process EXTRA_CERTIFICATE
            val rawCert = intent.getByteArrayExtra(EXTRA_CERTIFICATE) ?: throw IllegalArgumentException("EXTRA_CERTIFICATE required")

            val certFactory = CertificateFactory.getInstance("X.509")!!
            val cert = certFactory.generateCertificate(ByteArrayInputStream(rawCert)) as? X509Certificate
            this@Model.cert = cert

            if (cert != null)
                try {
                    val subject = cert.subjectAlternativeNames?.let { altNames ->
                        val sb = StringBuilder()
                        for (altName in altNames) {
                            val name = altName[1]
                            if (name is String)
                                sb.append("[").append(altName[0]).append("]").append(name).append(" ")
                        }
                        sb.toString()
                    } ?: /* use CN if alternative names are not available */ cert.subjectDN.name
                    issuedFor.emit(subject)

                    issuedBy.postValue(cert.issuerDN.toString())

                    val formatter = DateFormat.getDateInstance(DateFormat.LONG)
                    validFrom.postValue(formatter.format(cert.notBefore))
                    validTo.postValue(formatter.format(cert.notAfter))

                    sha1.postValue("SHA1: " + CertUtils.fingerprint(cert, SHA1.digestAlgorithm))
                    sha256.postValue("SHA256: " + CertUtils.fingerprint(cert, SHA256.digestAlgorithm))

                } catch(e: CertificateParsingException) {
                    Cert4Android.log.log(Level.WARNING, "Couldn't parse certificate", e)
                }

            // process EXTRA_TRUSTED
            if (intent.hasExtra(EXTRA_TRUSTED)) {
                val trusted = intent.getBooleanExtra(EXTRA_TRUSTED, false)
                registerDecision(trusted)
            }
        }

        fun registerDecision(trusted: Boolean) = viewModelScope.launch {
            // notify user decision registry
            cert?.let {
                UserDecisionRegistry.getInstance(getApplication()).onUserDecision(it, trusted)
            }

            // notify UI that the case has been decided (causes Activity to finish)
            decided.emit(true)
        }

    }

}