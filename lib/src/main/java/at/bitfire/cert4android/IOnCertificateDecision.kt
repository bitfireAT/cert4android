package at.bitfire.cert4android

interface IOnCertificateDecision {
    fun accept()
    fun reject()
}