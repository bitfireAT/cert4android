package at.bitfire.cert4android

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.runBlocking
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

    internal val pendingDecisions = mutableMapOf<X509Certificate, MutableList<Continuation<Boolean>>>()

    /**
     * Tries to retrieve a trust decision from the user about a given certificate.
     *
     * Thread-safe, can handle multiple requests for various certificates and/or the same certificate at once.
     *
     * @param cert              certificate to ask user about
     * @param getUserDecision   anonymous function to retrieve user decision
     * @return *true* if the user explicitly trusts the certificate, *false* if unknown or untrusted
     */
    suspend fun check(cert: X509Certificate, getUserDecision: suspend (X509Certificate) -> Boolean): Boolean = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            // remove from pending decisions on cancellation
            synchronized(pendingDecisions) {
                pendingDecisions[cert]?.remove(cont)
            }
        }

        val requestDecision: Boolean
        synchronized(pendingDecisions) {
            if (pendingDecisions.containsKey(cert)) {
                // There are already pending decisions for this request, just add our request
                pendingDecisions[cert]!! += cont
                requestDecision = false
            } else {
                // First decision for this certificate, show UI
                pendingDecisions[cert] = mutableListOf(cont)
                requestDecision = true
            }
        }

        if (requestDecision)
            runBlocking {
                requestDecision(cert, getUserDecision)
            }
    }

    /**
     * ...
     *
     */
    internal suspend fun requestDecision(cert: X509Certificate, getUserDecision: suspend (X509Certificate) -> Boolean) {
        val userDecision = getUserDecision(cert)
        onUserDecision(cert, userDecision)
    }

    fun onUserDecision(cert: X509Certificate, trusted: Boolean) {
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