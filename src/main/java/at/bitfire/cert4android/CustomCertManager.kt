/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.SparseArray
import java.io.Closeable
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * TrustManager to handle custom certificates. Communicates with
 * {@link CustomCertService} to fetch information about custom certificate
 * trustworthiness. The IPC with a service is required when multiple processes,
 * each of them with an own {@link CustomCertManager}, want to access a synchronized central
 * certificate trust store + UI (for accepting certificates etc.).
 */
class CustomCertManager: X509TrustManager, Closeable {

    companion object {
        /** how log to wait for a decision from {@link CustomCertService} */
        @JvmField
        var SERVICE_TIMEOUT: Long = 5 * 60 * 1000

        val nextDecisionID = AtomicInteger()
        val decisions = SparseArray<Boolean?>()
        val decisionLock = Object()

        /** thread to receive replies from {@link CustomCertService} */
        val messengerThread = HandlerThread("CustomCertificateManager.Messenger")
        init {
            messengerThread.start()
        }

        /** messenger to receive replies from {@link CustomCertService} */
        val messenger = Messenger(Handler(messengerThread.looper, MessageHandler()))

        @JvmField
        val MSG_CERTIFICATE_DECISION = 0

        // Messenger for receiving replies from CustomCertificateService
        private class MessageHandler : Handler.Callback {
            override fun handleMessage(msg: Message): Boolean {
                Constants.log.fine("Received reply from CustomCertificateService: " + msg)
                when (msg.what) {
                    MSG_CERTIFICATE_DECISION ->
                        synchronized(decisionLock) {
                            decisions.put(msg.arg1, msg.arg2 != 0)
                            decisionLock.notifyAll()
                            return true
                        }
                    else ->
                        return false
                }
            }
        }
    }

    val context: Context

    /** for sending requests to {@link CustomCertService} */
    var service: Messenger? = null
    val serviceConnection : ServiceConnection?

    /** system-default trust store */
    val systemTrustManager : X509TrustManager?

    /** Whether to launch {@link TrustCertificateActivity} directly. The notification will always be shown. */
    @JvmField
    var appInForeground = false


    /**
     * Creates a new instance, using a certain {@link CustomCertService} messenger (for testing)
     * @param context used to bind to {@link CustomCertService}
     * @param trustSystemCerts whether to trust system/user-installed CAs (default trust store)
     * @param service          messenger connected with {@link CustomCertService}
     */
    constructor(context: Context, trustSystemCerts: Boolean, service: Messenger?) {
        this.context = context

        systemTrustManager = if (trustSystemCerts) CertUtils.getTrustManager(null) else null

        if (service != null) {
            // use a specific service, primarily for testing
            this.service = service
            serviceConnection = null

        } else {
            serviceConnection = object: ServiceConnection {
                override fun onServiceConnected(className: ComponentName, binder: IBinder) {
                    Constants.log.fine("Connected to service")
                    this@CustomCertManager.service = Messenger(binder)
                }

                override fun onServiceDisconnected(className: ComponentName) {
                    this@CustomCertManager.service = null
                }
            }

            if (!context.bindService(Intent(context, CustomCertService::class.java), serviceConnection, Context.BIND_AUTO_CREATE))
                Constants.log.severe("Couldn't bind CustomCertService to context")
        }
    }

    /**
     * Creates a new instance.
     * @param context used to bind to {@link CustomCertService}
     * @param trustSystemCerts whether to trust system/user-installed CAs (default trust store)
     */
    constructor(context: Context, trustSystemCerts: Boolean)
            : this(context, trustSystemCerts, null)

    override fun close() {
        if (serviceConnection != null)
            context.unbindService(serviceConnection)
    }


    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        throw CertificateException("cert4android doesn't validate client certificates")
    }

    /**
     * Checks whether a certificate is trusted. If {@link #systemTrustManager} is null (because
     * system certificates are not being trusted or available), the first certificate in the chain
     * (which is the lowest one, i.e. the actual server certificate) is passed to
     * {@link CustomCertService} for further decision.
     * @param chain        certificate chain to check
     * @param authType     authentication type (ignored)
     * @throws CertificateException in case of an untrusted or questionable certificate
     */
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType : String) {
        var trusted = false

        if (systemTrustManager != null)
            try {
                systemTrustManager.checkServerTrusted(chain, authType)
                trusted = true
            } catch(ignored: CertificateException) {
                Constants.log.fine("Certificate not trusted by system")
            }

        if (!trusted)
            checkCustomTrusted(chain[0])
    }

    internal fun checkCustomTrusted(cert: X509Certificate) {
        Constants.log.fine("Querying custom certificate trustworthiness")

        val service : Messenger = this.service ?: throw CertificateException("Custom certificate service not available")

        var msg = Message.obtain()
        msg.what = CustomCertService.MSG_CHECK_TRUSTED
        msg.arg1 = nextDecisionID.getAndIncrement()
        val id = msg.arg1
        msg.replyTo = messenger

        val data = Bundle()
        data.putSerializable(CustomCertService.MSG_DATA_CERTIFICATE, cert)
        data.putBoolean(CustomCertService.MSG_DATA_APP_IN_FOREGROUND, appInForeground)
        msg.data = data

        try {
            service.send(msg)
        } catch(e: RemoteException) {
            throw CertificateException("Couldn't query custom certificate trustworthiness", e)
        }

        // wait for a reply
        val startTime = System.currentTimeMillis()
        synchronized(decisionLock) {
            while (System.currentTimeMillis() < startTime + SERVICE_TIMEOUT) {
                decisionLock.wait(SERVICE_TIMEOUT)
                val decision = decisions.get(id)
                if (decision != null) {
                    decisions.delete(id)
                    if (decision)
                    // certificate trusted
                        return
                    else
                        throw CertificateException("Certificate not trusted")
                }
            }
        }

        // timeout occurred
        msg = Message.obtain()
        msg.what = CustomCertService.MSG_CHECK_TRUSTED_ABORT
        msg.arg1 = id
        msg.replyTo = messenger

        val data2 = Bundle()
        data2.putSerializable(CustomCertService.MSG_DATA_CERTIFICATE, cert)
        msg.data = data2

        try {
            service.send(msg)
        } catch(e: RemoteException) {
            Constants.log.log(Level.WARNING, "Couldn't abort trustworthiness check", e)
        }

        throw CertificateException("Timeout when waiting for certificate trustworthiness decision")
    }

    override fun getAcceptedIssuers() : Array<X509Certificate> {
        return arrayOf()
    }


    // custom methods

    fun hostnameVerifier(defaultVerifier: HostnameVerifier?) : HostnameVerifier {
        return CustomHostnameVerifier(defaultVerifier)
    }

    fun resetCertificates() {
        val intent = Intent(context, CustomCertService::class.java)
        intent.action = CustomCertService.CMD_RESET_CERTIFICATES
        context.startService(intent)
    }


    // hostname verifier

    private inner class CustomHostnameVerifier(
            val defaultVerifier: HostnameVerifier?
    ): HostnameVerifier {

        override fun verify(host: String, sslSession: SSLSession): Boolean {
            Constants.log.fine("Verifying certificate for " + host)

            if (defaultVerifier?.verify(host, sslSession) == true)
                return true

            // default hostname verifier couldn't verify the hostname →
            // accept the hostname as verified only if the certificate has been accepted be the user

            try {
                val cert = sslSession.peerCertificates
                if (cert.isNotEmpty() && cert[0] is X509Certificate) {
                    checkCustomTrusted(cert[0] as X509Certificate)
                    Constants.log.fine("Certificate is in custom trust store, accepting")
                    return true
                }
            } catch(e: SSLPeerUnverifiedException) {
                Constants.log.log(Level.WARNING, "Couldn't get certificate for host name verification", e)
            } catch (ignored: CertificateException) {
            }

            return false
        }

    }

}
