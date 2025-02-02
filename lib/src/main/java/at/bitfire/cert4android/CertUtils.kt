/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.logging.Level
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object CertUtils {

    fun getTrustManager(keyStore: KeyStore?): X509TrustManager? {
        try {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            tmf.trustManagers
                .filterIsInstance<X509TrustManager>()
                .forEach { return it }
        } catch(e: GeneralSecurityException) {
            Cert4Android.log.log(Level.SEVERE, "Couldn't initialize trust manager", e)
        }
        return null
    }


    fun getTag(cert: X509Certificate) =
        fingerprint(cert, "SHA-512", separator = null)

    fun fingerprint(cert: X509Certificate, algorithm: String, separator: Char? = ':'): String {
        val md = MessageDigest.getInstance(algorithm)
        return hexString(md.digest(cert.encoded), separator)
    }

    fun hexString(data: ByteArray, separator: Char? = ':'): String {
        val str = StringBuilder()
        for ((idx, b) in data.withIndex()) {
            if (idx != 0 && separator != null)
                str.append(separator)
            str.append(String.format("%02x", b).uppercase(Locale.ROOT))
        }
        return str.toString()
    }

}