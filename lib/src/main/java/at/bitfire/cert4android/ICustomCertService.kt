package at.bitfire.cert4android

import java.security.cert.X509Certificate

interface ICustomCertService {
    suspend fun checkTrusted(cert: X509Certificate, interactive: Boolean, foreground: Boolean): Boolean
    fun abortCheck(certificate: X509Certificate)
}
