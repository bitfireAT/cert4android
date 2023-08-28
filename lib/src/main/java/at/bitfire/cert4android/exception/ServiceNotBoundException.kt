package at.bitfire.cert4android.exception

import java.security.cert.CertificateException

class ServiceNotBoundException: CertificateException("Not bound to CustomCertService")
