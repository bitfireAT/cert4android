/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Message;
import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;

public class CustomCertManagerTest extends InstrumentationTestCase {

    CustomCertManager certManager, paranoidCertManager;
    static {
        CustomCertManager.SERVICE_TIMEOUT = 1000;
    }

    @Override
    protected void setUp() throws Exception {
        certManager = new CustomCertManager(getInstrumentation().getContext(), true);
        certManager.resetCertificates();

        paranoidCertManager = new CustomCertManager(getInstrumentation().getContext(), false);
        paranoidCertManager.resetCertificates();
    }

    @Override
    protected void tearDown() throws Exception {
        paranoidCertManager.close();
        certManager.close();
    }


    public void testCheckClientCertificate() {
        try {
            certManager.checkClientTrusted(null, null);
            fail();
        } catch(CertificateException ignored) {
        }
    }

    public void testTrustedCertificate() throws IOException, CertificateException, InterruptedException {
        X509Certificate[] certs = getSiteCertificates(new URL("https://davdroid.bitfire.at"));

        // check with trusting system certificates
        certManager.checkServerTrusted(certs, "RSA");

        // check without trusting system certificates
        try {
            paranoidCertManager.checkServerTrusted(certs, "RSA");
            fail();
        } catch(CertificateException e) {
            assertTrue(e.getMessage().contains("Timeout"));
        }

        // add certificate and check again
        Intent intent = new Intent(getInstrumentation().getContext(), CustomCertService.class);
        intent.setAction(CustomCertService.CMD_CERTIFICATION_DECISION);
        intent.putExtra(CustomCertService.EXTRA_CERTIFICATE, certs[0]);
        intent.putExtra(CustomCertService.EXTRA_TRUSTED, true);
        Thread.sleep(1000);
        getInstrumentation().getContext().startService(intent);
        paranoidCertManager.checkServerTrusted(certs, "RSA");

        // remove certificate and check again
        // should now be rejected for the whole session, i.e. no timeout anymore
        intent.putExtra(CustomCertService.EXTRA_TRUSTED, false);
        getInstrumentation().getContext().startService(intent);
        Thread.sleep(1000);
        try {
            paranoidCertManager.checkServerTrusted(certs, "RSA");
            fail();
        } catch(CertificateException e) {
            assertTrue(e.getMessage().contains("not trusted"));
        }
    }


    private X509Certificate[] getSiteCertificates(URL url) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        try {
            conn.getInputStream().read();
            return (X509Certificate[])conn.getServerCertificates();
        } finally {
            conn.disconnect();
        }
    }

}
