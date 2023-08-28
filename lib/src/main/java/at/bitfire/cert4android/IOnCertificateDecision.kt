package at.bitfire.cert4android

@Deprecated("Replaced by Kotlin coroutines")
interface IOnCertificateDecision {
    fun accept()
    fun reject()
}
