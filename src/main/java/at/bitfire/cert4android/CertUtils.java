/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class CertUtils {

    @Nullable
    public static X509TrustManager getTrustManager(@Nullable KeyStore keyStore) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(keyStore);
            for (TrustManager trustManager : tmf.getTrustManagers())
                if (trustManager instanceof X509TrustManager)
                    return (X509TrustManager)trustManager;
        } catch(NoSuchAlgorithmException|KeyStoreException e) {
            Constants.log.log(Level.SEVERE, "Couldn't initialize trust manager", e);
        }
        return null;
    }

    @NonNull
    public static String getTag(@NonNull X509Certificate cert) {
        StringBuilder sb = new StringBuilder();
        for (byte b: cert.getSignature())
            sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

}
