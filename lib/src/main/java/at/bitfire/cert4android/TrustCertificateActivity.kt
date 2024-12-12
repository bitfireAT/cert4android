/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        fun rawCertFromIntent(intent: Intent): ByteArray =
            intent.getByteArrayExtra(EXTRA_CERTIFICATE) ?: throw IllegalArgumentException("EXTRA_CERTIFICATE required")
    }

    private val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        processIntent(intent)
        addOnNewIntentListener { newIntent ->
            processIntent(newIntent)
        }

        enableEdgeToEdge()

        setContent {
            Cert4Android.theme {
                MainLayout(
                    onRegisterDecision = { trusted -> model.registerDecision(trusted) },
                    onFinish = { finish() }
                )
            }
        }
    }

    private fun processIntent(intent: Intent) {
        // process certificate
        model.parseCertificate(rawCertFromIntent(intent))

        // process EXTRA_TRUSTED, if available
        if (intent.hasExtra(EXTRA_TRUSTED)) {
            val trusted = intent.getBooleanExtra(EXTRA_TRUSTED, false)
            model.registerDecision(trusted)
        }
    }


    @Composable
    @Preview
    fun MainLayout(
        onRegisterDecision: (Boolean) -> Unit = {},
        onFinish: () -> Unit = {}
    ) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val uiState = model.uiState
        LaunchedEffect(uiState.decided) {
            if (uiState.decided)
                onFinish()
        }

        var backPressedCounter by remember { mutableIntStateOf(0) }
        BackHandler {
            val newBackPressedCounter = backPressedCounter + 1
            when (newBackPressedCounter) {
                0 -> { /* back button not pressed yet */ }
                1 ->
                    scope.launch {
                        snackbarHostState.showSnackbar(getString(R.string.trust_certificate_press_back_to_reject))
                    }
                else ->
                    onRegisterDecision(false)
            }
            backPressedCounter = newBackPressedCounter
        }

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
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                CertificateCard(
                    uiState = uiState,
                    onRegisterDecision = onRegisterDecision
                )

                Text(
                    text = stringResource(R.string.trust_certificate_reset_info),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        }
    }

    @Composable
    fun CertificateCard(
        uiState: UiState,
        onRegisterDecision: (Boolean) -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.trust_certificate_x509_certificate_details),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                if (uiState.issuedFor != null)
                    InfoPack(R.string.trust_certificate_issued_for, uiState.issuedFor)
                if (uiState.issuedBy != null)
                    InfoPack(R.string.trust_certificate_issued_by, uiState.issuedBy)

                val validFrom = uiState.validFrom
                val validTo = uiState.validTo
                if (validFrom != null && validTo != null)
                    InfoPack(
                        R.string.trust_certificate_validity_period,
                        stringResource(
                            R.string.trust_certificate_validity_period_value,
                            validFrom,
                            validTo
                        )
                    )

                val sha1 = uiState.sha1
                val sha256 = uiState.sha256
                if (sha1 != null || sha256 != null) {
                    Text(
                        text = stringResource(R.string.trust_certificate_fingerprints).uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (sha1 != null)
                        Text(
                            text = sha1,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp, top = 4.dp),
                        )

                    if (sha256 != null)
                        Text(
                            text = sha256,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp, top = 4.dp),
                        )
                }

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
                            .clickable {
                                fingerprintVerified = !fingerprintVerified
                            }
                            .weight(1f)
                            .padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        enabled = fingerprintVerified,
                        onClick = {
                            onRegisterDecision(true)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) { Text(stringResource(R.string.trust_certificate_accept).uppercase()) }
                    TextButton(
                        onClick = {
                            onRegisterDecision(false)
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
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth(),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }


    data class UiState(
        val issuedFor: String? = null,
        val issuedBy: String? = null,
        val validFrom: String? = null,
        val validTo: String? = null,
        val sha1: String? = null,
        val sha256: String? = null,

        val decided: Boolean = false
    )

    class Model(application: Application) : AndroidViewModel(application) {

        private var cert: X509Certificate? = null

        var uiState by mutableStateOf(UiState())
            private set

        fun parseCertificate(rawCert: ByteArray) = viewModelScope.launch(Dispatchers.Default) {
            val certFactory = CertificateFactory.getInstance("X.509")!!
            (certFactory.generateCertificate(ByteArrayInputStream(rawCert)) as? X509Certificate)?.let { cert ->
                this@Model.cert = cert

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

                    val timeFormatter = DateFormat.getDateInstance(DateFormat.LONG)
                    Snapshot.withMutableSnapshot {      // thread-safe update of UI state
                        uiState = uiState.copy(
                            issuedFor = subject,
                            issuedBy = cert.issuerDN.toString(),
                            validFrom = timeFormatter.format(cert.notBefore),
                            validTo = timeFormatter.format(cert.notAfter),
                            sha1 = "SHA1: " + CertUtils.fingerprint(cert, SHA1.digestAlgorithm),
                            sha256 = "SHA256: " + CertUtils.fingerprint(cert, SHA256.digestAlgorithm)
                        )
                    }
                } catch (e: CertificateParsingException) {
                    Cert4Android.log.log(Level.WARNING, "Couldn't parse certificate", e)
                }
            }
        }

        fun registerDecision(trusted: Boolean) {
            // notify user decision registry
            cert?.let {
                UserDecisionRegistry.getInstance(getApplication()).onUserDecision(it, trusted)

                // notify UI that the case has been decided (causes Activity to finish)
                uiState = uiState.copy(decided = true)
            }
        }

    }

}