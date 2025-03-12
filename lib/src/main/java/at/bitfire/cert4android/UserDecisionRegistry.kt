package at.bitfire.cert4android

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.cert.X509Certificate
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class UserDecisionRegistry private constructor(
    private val context: Context
) {

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var instance: UserDecisionRegistry? = null

        @Synchronized
        fun getInstance(context: Context): UserDecisionRegistry {
            instance?.let {
                return it
            }

            val newInstance = UserDecisionRegistry(context.applicationContext)
            instance = newInstance
            return newInstance
        }

    }

    /**
     * Per-certificate map of pending decisions, which are [Continuation]s that are
     *   - resumed when the callback returns a decision and
     *   - cancelled when the scope is cancelled.
     *
     * Every call of [check] adds an entry to the [Continuation] list.
     */
    internal val pendingDecisions = mutableMapOf<X509Certificate, MutableList<Continuation<Boolean>>>()

    /**
     * Tries to retrieve a trust decision from the user about a given certificate.
     *
     * Thread-safe, can handle multiple requests for various certificates and/or the same certificate at once.
     *
     * @param cert              certificate to ask user about
     * @param scope             the coroutine scope within which [getUserDecision] is executed
     * @param getUserDecision   suspendable function to retrieve user decision
     * @return                  *true* if the user explicitly trusts the certificate; *false* if
     *                          unknown or untrusted
     */
    suspend fun check(
        cert: X509Certificate,
        scope: CoroutineScope,
        getUserDecision: suspend (X509Certificate) -> Boolean
    ): Boolean = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            // remove from pending decisions on cancellation
            synchronized(pendingDecisions) {
                pendingDecisions[cert]?.remove(cont)
            }
        }

        synchronized(pendingDecisions) {
            if (pendingDecisions.containsKey(cert)) {
                // There are already pending decisions for this request, just add our request
                pendingDecisions[cert]!! += cont

            } else {
                // First decision for this certificate, add to map and show UI
                pendingDecisions[cert] = mutableListOf(cont)

                scope.launch {
                    val userDecision = getUserDecision(cert)    // suspends until user decision is made

                    // resume all coroutines that are waiting for a decision
                    resumeOnUserDecision(cert, userDecision)
                }
            }
        }

        // Now the coroutine is suspended, and will be resumed when the user has made a decision using cont.resume()
    }

    fun resumeOnUserDecision(cert: X509Certificate, trusted: Boolean) {
        // save decision
        val customCertStore = CustomCertStore.getInstance(context)
        if (trusted)
            customCertStore.setTrustedByUser(cert)
        else
            customCertStore.setUntrustedByUser(cert)

        // continue work that's waiting for decisions
        synchronized(pendingDecisions) {
            pendingDecisions[cert]?.iterator()?.let { iter ->
                while (iter.hasNext()) {
                    iter.next().resume(trusted)
                    iter.remove()
                }
            }

            // remove certificate from pendingDecisions so UI can be shown again in future
            pendingDecisions.remove(cert)
        }
    }

}