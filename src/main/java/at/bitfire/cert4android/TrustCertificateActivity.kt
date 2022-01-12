/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.cert4android.databinding.ActivityTrustCertificateBinding
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec.SHA1
import java.security.spec.MGF1ParameterSpec.SHA256
import java.text.DateFormat
import java.util.logging.Level
import kotlin.concurrent.thread

class TrustCertificateActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_CERTIFICATE = "certificate"
    }

    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProvider(this)[Model::class.java]
        model.processIntent(intent)

        val binding = DataBindingUtil.setContentView<ActivityTrustCertificateBinding>(this, R.layout.activity_trust_certificate)
        binding.lifecycleOwner = this
        binding.model = model

        binding.acceptCertificate.setOnClickListener {
            sendDecision(true)
            finish()
        }
        binding.rejectCertificate.setOnClickListener {
            sendDecision(false)
            finish()
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


    class Model: ViewModel() {

        companion object {
            val certFactory = CertificateFactory.getInstance("X.509")!!
        }

        val issuedFor = MutableLiveData<String>()
        val issuedBy = MutableLiveData<String>()

        val validFrom = MutableLiveData<String>()
        val validTo = MutableLiveData<String>()

        val sha1 = MutableLiveData<String>()
        val sha256 = MutableLiveData<String>()

        val verifiedByUser = MutableLiveData<Boolean>()

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