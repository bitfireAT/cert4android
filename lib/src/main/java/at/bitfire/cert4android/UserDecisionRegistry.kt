package at.bitfire.cert4android

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
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

    private val pendingDecisions = mutableMapOf<X509Certificate, MutableList<Continuation<Boolean>>>()

    suspend fun check(cert: X509Certificate, appInForeground: Boolean): Boolean = suspendCancellableCoroutine { cont ->
        // check whether we're able to retrieve user feedback (= start an Activity and/or show a notification)
        val notificationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else
                true
        val userDecisionPossible = appInForeground || notificationPermission

        if (userDecisionPossible) {
            // User decision possible → remember request in pendingDecisions so that a later decision will be applied to this request

            cont.invokeOnCancellation {
                // remove from pending decisions on cancellation
                synchronized(pendingDecisions) {
                    pendingDecisions[cert]?.remove(cont)
                }

                val nm = NotificationUtils.createChannels(context)
                nm.cancel(CertUtils.getTag(cert), NotificationUtils.ID_CERT_DECISION)
            }

            synchronized(pendingDecisions) {
                if (pendingDecisions.containsKey(cert)) {
                    // There are already pending decisions for this request, just add our request
                    pendingDecisions[cert]!! += cont
                } else {
                    // First decision for this certificate, show UI
                    pendingDecisions[cert] = mutableListOf(cont)
                    retrieveDecision(cert, launchActivity = appInForeground, showNotification = notificationPermission)
                }
            }
        } else {
            // We're not able to retrieve user feedback, directly reject request
            Cert4Android.log.warning("App not in foreground and missing notification permission, rejecting certificate")
            cont.resume(false)
        }
    }

    /**
     * Starts UI for retrieving feedback (accept/reject) for a certificate from the user.
     *
     * Ensure that required permissions are granted/conditions are met before setting [launchActivity]
     * or [showNotification].
     *
     * @param cert              certificate to ask user about
     * @param launchActivity    whether to launch a [TrustCertificateActivity]
     * @param showNotification  whether to show a certificate notification
     *
     * @throws IllegalArgumentException  when both [launchActivity] and [showNotification] are *false*
     */
    private fun retrieveDecision(cert: X509Certificate, launchActivity: Boolean, showNotification: Boolean) {
        if (!launchActivity && !showNotification)
            throw IllegalArgumentException("User decision requires certificate Activity and/or notification")

        val rawCert = cert.encoded
        val decisionIntent = Intent(context, TrustCertificateActivity::class.java).apply {
            putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, rawCert)
        }

        if (showNotification) {
            val rejectIntent = Intent(context, TrustCertificateActivity::class.java).apply {
                putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, rawCert)
                putExtra(TrustCertificateActivity.EXTRA_TRUSTED, false)
            }

            val id = rawCert.contentHashCode()
            val notify = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_CERTIFICATES)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_lock_open_white)
                .setContentTitle(context.getString(R.string.certificate_notification_connection_security))
                .setContentText(context.getString(R.string.certificate_notification_user_interaction))
                .setSubText(cert.subjectDN.name)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(PendingIntent.getActivity(context, id, decisionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setDeleteIntent(PendingIntent.getActivity(context, id + 1, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .build()

            val nm = NotificationUtils.createChannels(context)
            nm.notify(CertUtils.getTag(cert), NotificationUtils.ID_CERT_DECISION, notify)
        }

        if (launchActivity) {
            decisionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(decisionIntent)
        }
    }

    fun onUserDecision(cert: X509Certificate, trusted: Boolean) {
        // cancel notification
        val nm = NotificationUtils.createChannels(context)
        nm.cancel(CertUtils.getTag(cert), NotificationUtils.ID_CERT_DECISION)

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