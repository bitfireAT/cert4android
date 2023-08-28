package at.bitfire.cert4android

import java.security.cert.X509Certificate

interface ICustomCertService {
    @Deprecated(
        message = "Callbacks no longer used",
        replaceWith = ReplaceWith("checkTrusted(rawCert, interactive, foreground)")
    )
    fun checkTrusted(rawCert: ByteArray, interactive: Boolean, foreground: Boolean, callback: IOnCertificateDecision)

    /**
     * Checks whether the user trusts the given certificate.
     *
     * This function can take a long time to complete, if [interactive] is `true`, for example, and the user takes a
     * while to confirm.
     * This is why it's recommended to add a timeout.
     *
     * Example:
     * ```kotlin
     * withTimeout(30000) {
     *     val isTrusted = svc.checkTrusted(cert, interactive, appInForeground)
     *
     *     if (!isTrusted) {
     *         throw CertificateNotTrustedException(cert)
     *     }
     * }
     * ```
     * If the coroutine running the function is canceled, [abortCheck] is called automatically with the certificate given.
     *
     * @since 2023-08-28
     *
     * @param cert The certificate to check for trust-worthiness.
     * @param interactive Whether the user can interact with the decision.
     * This means that if the certificate is not trusted, a notification will pop up asking the user for confirmation.
     * If the certificate is not trusted, and [interactive] is `false`, this function will return false.
     * @param foreground Whether this function is running in the foreground.
     * If true, the notification will still be shown, but the confirmation activity will be launched automatically.
     *
     * @return `true` if the certificate is trusted. `false` otherwise.
     */
    suspend fun checkTrusted(cert: X509Certificate, interactive: Boolean, foreground: Boolean): Boolean

    @Deprecated(
        message = "Callbacks no longer used",
        replaceWith = ReplaceWith("abortCheck(completable)")
    )
    fun abortCheck(callback: IOnCertificateDecision)

    /**
     * Abort all checks being made to the given certificate.
     */
    fun abortCheck(certificate: X509Certificate)
}
