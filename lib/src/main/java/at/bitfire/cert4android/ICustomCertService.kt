package at.bitfire.cert4android

import kotlinx.coroutines.CompletableDeferred

interface ICustomCertService {
    @Deprecated(
        message = "Callbacks no longer used",
        replaceWith = ReplaceWith("checkTrusted(rawCert, interactive, foreground)")
    )
    fun checkTrusted(rawCert: ByteArray, interactive: Boolean, foreground: Boolean, callback: IOnCertificateDecision)

    fun checkTrusted(rawCert: ByteArray, interactive: Boolean, foreground: Boolean): CompletableDeferred<Boolean>

    @Deprecated(
        message = "Callbacks no longer used",
        replaceWith = ReplaceWith("abortCheck(completable)")
    )
    fun abortCheck(callback: IOnCertificateDecision)

    fun abortCheck(completable: CompletableDeferred<Boolean>)
}
