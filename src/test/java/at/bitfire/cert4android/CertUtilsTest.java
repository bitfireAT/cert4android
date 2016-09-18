/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CertUtilsTest {

    @Test
    public void getTrustManagerSystem() {
        Assert.assertNotNull(CertUtils.getTrustManager(null));
    }

    @Test
    public void getTag() throws CertificateException, IOException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        InputStream is = getClass().getClassLoader().getResourceAsStream("davdroid-web.crt");
        try {
            X509Certificate cert = (X509Certificate)factory.generateCertificate(is);
            Assert.assertNotNull(cert);

            Assert.assertEquals("276126a80783ee16b84811e1e96e977a"+
                    "05ac0f980c586cc9784d95a804260c6d"+
                    "ddea1172266f210ef2d9463fee60afe7"+
                    "875274bdc65b91838f65ba566a51e55e"+
                    "143e7c40948eb5f314d253d36a695235"+
                    "c6df782e773f8455431e905d65d5d489"+
                    "a4e8afcfdc2dceb8ba5f706f71c75106"+
                    "caae8d4de5670d3721c722df11a0f377"+
                    "b13aca4525399954c31414dcb5449cbe"+
                    "3b444595b31952bb5782aff07d0d4ff3"+
                    "feefeabe8332a7fef47d64f29546a127"+
                    "e461ed972e5d1bbe0ebca916ed0fb03b"+
                    "81ec4c6019ac2f01b9f6c22dfbf4fb69"+
                    "0564874dc8e7ee3ac2ac0f29722ca353"+
                    "17865e1cac3c4a1fb9780fafd1c8763e"+
                    "1b4854d63067b91ece029833e9506b75", CertUtils.getTag(cert));
        } finally {
            is.close();
        }
    }

}
