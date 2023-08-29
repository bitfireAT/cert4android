package at.bitfire.cert4android

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        synchronized(pendingDecisions) {
            if (pendingDecisions.containsKey(cert))
                pendingDecisions[cert]!! += cont
            else
                pendingDecisions[cert] = mutableListOf(cont)
        }

        cont.invokeOnCancellation {
            synchronized(pendingDecisions) {
                pendingDecisions[cert]?.remove(cont)

                val nm = NotificationUtils.createChannels(context)
                nm.cancel(CertUtils.getTag(cert), NotificationUtils.ID_CERT_DECISION)
            }
        }

        // initiate user feedback
        val rawCert = cert.encoded
        val decisionIntent = Intent(context, TrustCertificateActivity::class.java)
        decisionIntent.putExtra(TrustCertificateActivity.EXTRA_CERTIFICATE, rawCert)

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
            .setDeleteIntent(PendingIntent.getService(context, id, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        val nm = NotificationUtils.createChannels(context)
        val notificationShown =
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                nm.notify(CertUtils.getTag(cert), NotificationUtils.ID_CERT_DECISION, notify)
                true
            } else
                false

        if (appInForeground) {
            decisionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(decisionIntent)
        } else if (!notificationShown) {
            Cert4Android.log.warning("App not in foreground and missing notification permission, rejecting certificate")

            // return false to caller of suspending function
            cont.resume(false)
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
        pendingDecisions[cert]?.iterator()?.let { iter ->
            while (iter.hasNext()) {
                iter.next().resume(trusted)
                iter.remove()
            }
        }
    }

}