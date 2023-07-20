package at.bitfire.cert4android

interface ICustomCertService {
    fun checkTrusted(rawCert: ByteArray, interactive: Boolean, foreground: Boolean, callback: IOnCertificateDecision)
    fun abortCheck(callback: IOnCertificateDecision)
}
