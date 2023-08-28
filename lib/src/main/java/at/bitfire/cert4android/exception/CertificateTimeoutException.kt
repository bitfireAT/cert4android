package at.bitfire.cert4android.exception

import java.security.cert.CertificateException

class CertificateTimeoutException: CertificateException("Timeout when waiting for certificate trustworthiness decision")
