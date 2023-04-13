/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.accompanist.themeadapter.material.MdcTheme
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec.SHA1
import java.security.spec.MGF1ParameterSpec.SHA256
import java.text.DateFormat
import java.util.logging.Level
import kotlin.concurrent.thread

class TrustCertificateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CERTIFICATE = "certificate"
    }

    private val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.processIntent(intent)

        setContent {
            MdcTheme {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.trust_certificate_unknown_certificate_found),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )

                    CertificateCard()

                    Text(
                        text = stringResource(R.string.trust_certificate_reset_info),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        model.processIntent(intent)
    }

    private fun sendDecision(trusted: Boolean) {
        val intent = Intent(this, CustomCertService::class.java)
        with(intent) {
            action = CustomCertService.CMD_CERTIFICATION_DECISION
            putExtra(CustomCertService.EXTRA_CERTIFICATE, getIntent().getSerializableExtra(EXTRA_CERTIFICATE))
            putExtra(CustomCertService.EXTRA_TRUSTED, trusted)
        }
        startService(intent)
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
                val issuedFor by model.issuedFor.observeAsState("")
                val issuedBy by model.issuedBy.observeAsState("")
                val validFrom by model.validFrom.observeAsState("")
                val validTo by model.validTo.observeAsState("")
                val sha1 by model.sha1.observeAsState("")
                val sha256 by model.sha256.observeAsState("")

                Text(
                    text = stringResource(R.string.trust_certificate_x509_certificate_details),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                InfoPack(
                    R.string.trust_certificate_issued_for,
                    issuedFor
                )
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
                    text = stringResource(R.string.trust_certificate_fingerprints),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
                Text(
                    text = sha1,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 4.dp),
                )
                Text(
                    text = sha256,
                    style = MaterialTheme.typography.bodyMedium,
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
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    TextButton(
                        enabled = fingerprintVerified,
                        onClick = {
                            sendDecision(true)
                            finish()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                    ) { Text(stringResource(R.string.trust_certificate_accept)) }
                    TextButton(
                        onClick = {
                            sendDecision(false)
                            finish()
                        },
                        modifier = Modifier
                            .weight(1f),
                    ) { Text(stringResource(R.string.trust_certificate_reject)) }
                }
            }
        }
    }

    @Composable
    fun InfoPack(@StringRes labelStringRes: Int, text: String) {
        Text(
            text = stringResource(labelStringRes),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth(),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }


    class Model : ViewModel() {

        companion object {
            val certFactory = CertificateFactory.getInstance("X.509")!!
        }

        val issuedFor = MutableLiveData<String>()
        val issuedBy = MutableLiveData<String>()

        val validFrom = MutableLiveData<String>()
        val validTo = MutableLiveData<String>()

        val sha1 = MutableLiveData<String>()
        val sha256 = MutableLiveData<String>()

        fun processIntent(intent: Intent?) {
            intent?.getByteArrayExtra(EXTRA_CERTIFICATE)?.let { raw ->
                thread {
                    val cert = certFactory.generateCertificate(ByteArrayInputStream(raw)) as? X509Certificate ?: return@thread

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
                        issuedFor.postValue(subject)

                        issuedBy.postValue(cert.issuerDN.toString())

                        val formatter = DateFormat.getDateInstance(DateFormat.LONG)
                        validFrom.postValue(formatter.format(cert.notBefore))
                        validTo.postValue(formatter.format(cert.notAfter))

                        sha1.postValue("SHA1: " + CertUtils.fingerprint(cert, SHA1.digestAlgorithm))
                        sha256.postValue("SHA256: " + CertUtils.fingerprint(cert, SHA256.digestAlgorithm))

                    } catch(e: CertificateParsingException) {
                        Constants.log.log(Level.WARNING, "Couldn't parse certificate", e)
                    }
                }
            }
        }

    }

}