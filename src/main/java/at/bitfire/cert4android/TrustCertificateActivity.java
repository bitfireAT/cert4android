/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.cert4android;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.LinkedList;
import java.util.List;

public class TrustCertificateActivity extends AppCompatActivity {

    public static final String EXTRA_CERTIFICATE = "certificate";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_trust_certificate);
        showCertificate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showCertificate();
    }

    protected void showCertificate() {
        X509Certificate cert = (X509Certificate)getIntent().getSerializableExtra(EXTRA_CERTIFICATE);

        final String subject;
        try {
            if (cert.getIssuerAlternativeNames() != null) {
                StringBuilder sb = new StringBuilder();
                for (List altName : cert.getSubjectAlternativeNames()) {
                    Object name = altName.get(1);
                    if (name instanceof String)
                        sb  .append("[").append(altName.get(0)).append("]")
                                .append(name).append(" ");
                }
                subject = sb.toString();
            } else
                subject = cert.getSubjectDN().getName();
            TextView tv = (TextView)findViewById(R.id.issuedFor);
            tv.setText(subject);

            tv = (TextView)findViewById(R.id.issuedBy);
            tv.setText(cert.getIssuerDN().toString());

            DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG);
            tv = (TextView)findViewById(R.id.validity_period);
            tv.setText(getString(R.string.trust_certificate_validity_period_value,
                    formatter.format(cert.getNotBefore()),
                    formatter.format(cert.getNotAfter())));

            tv = (TextView)findViewById(R.id.fingerprint_sha1);
            tv.setText(fingerprint(cert, "SHA-1"));
            tv = (TextView)findViewById(R.id.fingerprint_sha256);
            tv.setText(fingerprint(cert, "SHA-256"));
        } catch(CertificateParsingException e) {
            e.printStackTrace();
        }

        final Button btnAccept = (Button)findViewById(R.id.accept);
        CheckBox cb = (CheckBox)findViewById(R.id.fingerprint_ok);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean state) {
                btnAccept.setEnabled(state);
            }
        });
    }


    public void acceptCertificate(View view) {
        sendDecision(true);
        finish();
    }

    public void rejectCertificate(View view) {
        sendDecision(false);
        finish();
    }

    protected void sendDecision(boolean trusted) {
        Intent intent = new Intent(this, CustomCertService.class);
        intent.setAction(CustomCertService.CMD_CERTIFICATION_DECISION);
        intent.putExtra(CustomCertService.EXTRA_CERTIFICATE, getIntent().getSerializableExtra(EXTRA_CERTIFICATE));
        intent.putExtra(CustomCertService.EXTRA_TRUSTED, trusted);
        startService(intent);
    }


    private static String fingerprint(X509Certificate cert, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return algorithm + ": " + hexString(md.digest(cert.getEncoded()));
        } catch(NoSuchAlgorithmException|CertificateEncodingException e) {
            return e.getMessage();
        }
    }

    private static String hexString(byte[] data) {
        List<String> sb = new LinkedList<>();
        for (byte b : data)
            sb.add(Integer.toHexString(b & 0xFF));
        return TextUtils.join(":", sb);
    }

}
