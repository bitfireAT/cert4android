/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.conscrypt.Conscrypt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.Security
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.logging.Level
import javax.net.ssl.SSLContext

class CustomCertStore private constructor(
    private val context: Context
) {

    companion object {

        const val KEYSTORE_DIR = "KeyStore"
        const val KEYSTORE_NAME = "KeyStore.bks"

        @SuppressLint("StaticFieldLeak")
        private var instance: CustomCertStore? = null

        @Synchronized
        fun getInstance(context: Context): CustomCertStore {
            instance?.let {
                return it
            }

            val newInstance = CustomCertStore(context.applicationContext)
            instance = newInstance
            return newInstance
        }

    }

    /** system default TrustStore */
    private val systemKeyStore by lazy { Conscrypt.getDefaultX509TrustManager() }

    /** custom TrustStore */
    private val userKeyStoreFile by lazy { File(context.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE), KEYSTORE_NAME) }
    private val userKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())!!

    /** in-memory store for untrusted certs */
    private var untrustedCerts = HashSet<X509Certificate>()

    init {
        // initialize Conscrypt
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        val version = Conscrypt.version()
        Log.i(Cert4Android.TAG, "Using Conscrypt/${version.major()}.${version.minor()}.${version.patch()} for TLS")
        val engine = SSLContext.getDefault().createSSLEngine()
        Log.i(Cert4Android.TAG, "Enabled protocols: ${engine.enabledProtocols.joinToString(", ")}")
        Log.i(Cert4Android.TAG, "Enabled ciphers: ${engine.enabledCipherSuites.joinToString(", ")}")

        loadUserKeyStore()
    }

    @Synchronized
    fun clearUserDecisions() {
        // clear trusted certs
        for (alias in userKeyStore.aliases())
            userKeyStore.deleteEntry(alias)
        saveUserKeyStore()

        // clear untrusted certs
        untrustedCerts.clear()
    }

    /**
     * Determines whether a certificate chain is trusted.
     */
    fun isTrusted(chain: Array<X509Certificate>, authType: String, trustSystemCerts: Boolean, appInForeground: StateFlow<Boolean>?): Boolean {
        if (chain.isEmpty())
            throw IllegalArgumentException("Certificate chain must not be empty")
        val cert = chain[0]

        synchronized(this) {
            if (isTrustedByUser(cert))
                // explicitly accepted by user
                return true

            // check system certs, if applicable
            if (trustSystemCerts)
                try {
                    systemKeyStore.checkServerTrusted(chain, authType)

                    // trusted by system
                    return true
                } catch (ignored: CertificateException) {
                    // not trusted by system, ask user
                }

            // already rejected by user, don't ask again
            if (untrustedCerts.contains(cert))
                return false
        }

        if (appInForeground == null) {
            Cert4Android.log.log(Level.INFO, "Certificate not known and running in non-interactive mode, rejecting")
            return false
        }

        return runBlocking {
            val ui = UserDecisionUi.getInstance(context)
            val trusted = ui.check(cert, appInForeground)

            // save decision
            if (trusted) {
                userKeyStore.setCertificateEntry(CertUtils.getTag(cert), cert)
            } else {
                untrustedCerts += cert
            }

            trusted
        }
    }

    /**
     * Determines whether a certificate has been explicitly accepted by the user. In this case,
     * we can ignore an invalid host name for that certificate.
     *
     */
    fun isTrustedByUser(cert: X509Certificate): Boolean =
        synchronized(this) {
            userKeyStore.getCertificateAlias(cert) != null
        }

    private fun loadUserKeyStore() {
        try {
            FileInputStream(userKeyStoreFile).use {
                userKeyStore.load(it, null)
                Cert4Android.log.fine("Loaded ${userKeyStore.size()} trusted certificate(s)")
            }
        } catch(e: Exception) {
            Cert4Android.log.log(Level.INFO, "No key store for trusted certificates (yet); creating in-memory key store.")
            try {
                userKeyStore.load(null, null)
            } catch(e: Exception) {
                Cert4Android.log.log(Level.SEVERE, "Couldn't initialize in-memory key store", e)
            }
        }
    }

    private fun saveUserKeyStore() {
        try {
            FileOutputStream(userKeyStoreFile).use { userKeyStore.store(it, null) }
        } catch(e: Exception) {
            Cert4Android.log.log(Level.SEVERE, "Couldn't save custom certificate key store", e)
        }
    }

}