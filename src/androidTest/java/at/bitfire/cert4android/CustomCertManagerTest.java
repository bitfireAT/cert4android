/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android;

import android.content.Intent;
import android.os.IBinder;
import android.os.Messenger;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HttpsURLConnection;

import static android.support.test.InstrumentationRegistry.getContext;
import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class CustomCertManagerTest {

    CustomCertManager certManager, paranoidCertManager;
    static {
        CustomCertManager.SERVICE_TIMEOUT = 1000;
    }

    @Rule
    public ServiceTestRule serviceTestRule = new ServiceTestRule();

    Messenger service;

    static X509Certificate[] siteCerts;
    static {
        try {
            siteCerts = getSiteCertificates(new URL("https://davdroid.bitfire.at"));
        } catch(IOException e) {
        }
        assertNotNull(siteCerts);
    }


    @Before
    public void initCertManager() throws TimeoutException, InterruptedException {
        // prepare a bound and ready service for testing
        // loop required because of https://code.google.com/p/android/issues/detail?id=180396
        IBinder binder = bindService(CustomCertService.class);
        assertNotNull(binder);
        service = new Messenger(binder);

        certManager = new CustomCertManager(getContext(), true, service);
        assertNotNull(certManager);
        certManager.resetCertificates();

        paranoidCertManager = new CustomCertManager(getContext(), false, service);
        assertNotNull(paranoidCertManager);
        paranoidCertManager.resetCertificates();
    }

    @After
    public void closeCertManager() {
        paranoidCertManager.close();
        certManager.close();
    }


    @Test(expected = CertificateException.class)
    public void testCheckClientCertificate() throws CertificateException {
        certManager.checkClientTrusted(null, null);
    }

    @Test
    public void testTrustedCertificate() throws CertificateException, TimeoutException {
        certManager.checkServerTrusted(siteCerts, "RSA");
    }

    @Test(expected = CertificateException.class)
    public void testParanoidCertificate() throws CertificateException {
        paranoidCertManager.checkServerTrusted(siteCerts, "RSA");
    }

    @Test
    public void testAddCustomCertificate() throws CertificateException, TimeoutException, InterruptedException {
        addCustomCertificate();
        paranoidCertManager.checkServerTrusted(siteCerts, "RSA");
    }

    @Test(expected = CertificateException.class)
    public void testRemoveCustomCertificate() throws CertificateException, TimeoutException, InterruptedException {
        addCustomCertificate();

        // remove certificate and check again
        // should now be rejected for the whole session, i.e. no timeout anymore
        Intent intent = new Intent(getContext(), CustomCertService.class);
        intent.setAction(CustomCertService.CMD_CERTIFICATION_DECISION);
        intent.putExtra(CustomCertService.EXTRA_CERTIFICATE, siteCerts[0]);
        intent.putExtra(CustomCertService.EXTRA_TRUSTED, false);
        startService(intent, CustomCertService.class);
        paranoidCertManager.checkServerTrusted(siteCerts, "RSA");
    }

    private void addCustomCertificate() throws TimeoutException, InterruptedException {
        // add certificate and check again
        Intent intent = new Intent(getContext(), CustomCertService.class);
        intent.setAction(CustomCertService.CMD_CERTIFICATION_DECISION);
        intent.putExtra(CustomCertService.EXTRA_CERTIFICATE, siteCerts[0]);
        intent.putExtra(CustomCertService.EXTRA_TRUSTED, true);
        startService(intent, CustomCertService.class);
    }


    private IBinder bindService(Class clazz) throws TimeoutException, InterruptedException {
        IBinder binder = null;
        int it = 0;
        while ((binder = serviceTestRule.bindService(new Intent(getTargetContext(), clazz))) == null && it++ <10) {
            System.err.println("Waiting for ServiceTestRule.bindService");
            Thread.sleep(50);
        }
        return binder;
    }

    private void startService(Intent intent, Class clazz) throws TimeoutException, InterruptedException {
        serviceTestRule.startService(intent);
        bindService(clazz);
    }

    private static X509Certificate[] getSiteCertificates(URL url) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        try {
            conn.getInputStream().read();

            Certificate[] certs = conn.getServerCertificates();
            X509Certificate[] x509 = new X509Certificate[certs.length];
            System.arraycopy(certs, 0, x509, 0, certs.length);
            return x509;

        } finally {
            conn.disconnect();
        }
    }

}
