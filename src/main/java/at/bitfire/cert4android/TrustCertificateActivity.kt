/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.*
import java.util.logging.Level

class TrustCertificateActivity: AppCompatActivity() {

    companion object {
        val EXTRA_CERTIFICATE = "certificate"

        val certFactory = CertificateFactory.getInstance("X.509")!!
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_trust_certificate)
        showCertificate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        showCertificate()
    }

    private fun showCertificate() {
        val raw = intent.getByteArrayExtra(EXTRA_CERTIFICATE)
        (certFactory.generateCertificate(ByteArrayInputStream(raw)) as X509Certificate?)?.let { cert ->
            val subject: String
            try {
                subject = if (cert.issuerAlternativeNames != null) {
                    val sb = StringBuilder()
                    for (altName in cert.subjectAlternativeNames) {
                        val name = altName[1]
                        if (name is String)
                            sb.append("[").append(altName[0]).append("]").append(name).append(" ")
                    }
                    sb.toString()
                } else
                    cert.subjectDN.name

                var tv = findViewById<TextView>(R.id.issuedFor)
                tv.text = subject

                tv = findViewById<TextView>(R.id.issuedBy)
                tv.text = cert.issuerDN.toString()

                val formatter = DateFormat.getDateInstance(DateFormat.LONG)
                tv = findViewById<TextView>(R.id.validity_period)
                tv.text = getString(R.string.trust_certificate_validity_period_value,
                        formatter.format(cert.notBefore),
                        formatter.format(cert.notAfter))

                tv = findViewById<TextView>(R.id.fingerprint_sha1)
                tv.text = fingerprint(cert, "SHA-1")
                tv = findViewById<TextView>(R.id.fingerprint_sha256)
                tv.text = fingerprint(cert, "SHA-256")
            } catch(e: CertificateParsingException) {
                Constants.log.log(Level.WARNING, "Couldn't parse certificate", e)
            }
        }

        val btnAccept = findViewById<Button>(R.id.accept)
        val cb = findViewById<CheckBox>(R.id.fingerprint_ok)
        cb.setOnCheckedChangeListener { _, state -> btnAccept.isEnabled = state }
    }


    fun acceptCertificate(view: View) {
        sendDecision(true)
        finish()
    }

    fun rejectCertificate(view: View) {
        sendDecision(false)
        finish()
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


    private fun fingerprint(cert: X509Certificate, algorithm: String): String {
        try {
            val md = MessageDigest.getInstance(algorithm)
            return "$algorithm: ${hexString(md.digest(cert.encoded))}"
        } catch(e: Exception) {
            return e.message ?: "Couldn't create message digest"
        }
    }

    private fun hexString(data: ByteArray): String {
        val str = data.mapTo(LinkedList<String>()) { String.format("%02x", it) }
        return str.joinToString(":")
    }

}
