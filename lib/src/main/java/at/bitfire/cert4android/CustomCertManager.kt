/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * TrustManager to handle custom certificates. Communicates with
 * [CustomCertService] (singleton) to fetch information about custom certificate
 * trustworthiness. The service is required when multiple threads,
 * each of them with an own [CustomCertManager], want to access a synchronized central
 * certificate trust store + UI (for accepting certificates etc.).
 *
 * @param context used to bind to [CustomCertService]
 * @param interactive true: users will be notified in case of unknown certificates;
 *                    false: unknown certificates will be rejected (only uses custom certificate key store)
 * @param trustSystemCerts whether system certificates will be trusted
 * @param appInForeground  Whether to launch [TrustCertificateActivity] directly. The notification will always be shown.
 *
 * @constructor Creates a new instance, using a certain [CustomCertService] messenger (for testing).
 * Must not be run from the main thread because this constructor may request binding to [CustomCertService].
 * The actual binding code is called by the looper in the main thread, so waiting for the
 * service would block forever.
 *
 * @throws IllegalStateException if run from main thread
 */
@SuppressLint("CustomX509TrustManager")
class CustomCertManager @JvmOverloads constructor(
    private val context: Context,
    val trustSystemCerts: Boolean = true,
    val interactive: Boolean,
    var appInForeground: StateFlow<Boolean>
): X509TrustManager {

    private val certStore = CustomCertStore.getInstance(context)


    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        throw CertificateException("cert4android doesn't validate client certificates")
    }

    /**
     * Checks whether a certificate is trusted. If [systemTrustManager] is null (because
     * system certificates are not being trusted or available), the first certificate in the chain
     * (which is the lowest one, i.e. the actual server certificate) is passed to
     * [CustomCertService] for further decision.
     *
     * @param chain        certificate chain to check
     * @param authType     authentication type (ignored)
     *
     * @throws CertificateException in case of an untrusted or questionable certificate
     */
    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (!certStore.isTrusted(chain, authType, trustSystemCerts, appInForeground))
            throw CertificateException("Certificate chain not trusted")
    }

    override fun getAcceptedIssuers() = arrayOf<X509Certificate>()

}