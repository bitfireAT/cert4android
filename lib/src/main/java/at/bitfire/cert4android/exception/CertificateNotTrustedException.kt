package at.bitfire.cert4android.exception

import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class CertificateNotTrustedException(val cert: X509Certificate): CertificateException("The given certificate has not been trusted by the user.")
