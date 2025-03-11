package at.bitfire.cert4android

import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec.SHA1
import java.security.spec.MGF1ParameterSpec.SHA256
import java.text.DateFormat

/**
 * Human-readable certificate details.
 *
 * Usually created with [CertificateDetails.fromX509] and used by [TrustCertificateDialog].
 */
data class CertificateDetails(
    val issuedFor: String? = null,
    val issuedBy: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
    val sha1: String? = null,
    val sha256: String? = null
) {

    companion object {

        /**
         * Creates [CertificateDetails] from [X509Certificate].
         *
         * @param cert X509Certificate
         * @return CertificateDetails
         */
        fun fromX509(cert: X509Certificate): CertificateDetails {
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
            return CertificateDetails(
                issuedFor = subject,
                issuedBy = cert.issuerDN.toString(),
                validFrom = timeFormatter.format(cert.notBefore),
                validTo = timeFormatter.format(cert.notAfter),
                sha1 = CertUtils.fingerprint(cert, SHA1.digestAlgorithm),
                sha256 = CertUtils.fingerprint(cert, SHA256.digestAlgorithm)
            )
        }
    }

}