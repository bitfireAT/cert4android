/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.cert4android

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Show this dialog to the user to make a decision on whether to trust the given certificate.
 *
 * @param certificateDetails Certificate details
 * @param onSetTrustDecision Callback to set the users trust decision for given certificate
 */
@Composable
fun TrustCertificateDialog(
    certificateDetails: CertificateDetails,
    onSetTrustDecision: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Column(
            modifier = modifier
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.trust_certificate_x509_certificate_details),
                style = MaterialTheme.typography.titleMedium,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
            if (certificateDetails.issuedFor != null)
                InfoPack(R.string.trust_certificate_issued_for, certificateDetails.issuedFor)
            if (certificateDetails.issuedBy != null)
                InfoPack(R.string.trust_certificate_issued_by, certificateDetails.issuedBy)

            val validFrom = certificateDetails.validFrom
            val validTo = certificateDetails.validTo
            if (validFrom != null && validTo != null)
                InfoPack(
                    R.string.trust_certificate_validity_period,
                    stringResource(
                        R.string.trust_certificate_validity_period_value,
                        validFrom,
                        validTo
                    )
                )

            val sha1 = certificateDetails.sha1
            val sha256 = certificateDetails.sha256
            if (sha1 != null || sha256 != null) {
                Text(
                    text = stringResource(R.string.trust_certificate_fingerprints).uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = modifier.fillMaxWidth(),
                )

                if (sha1 != null)
                    Text(
                        text = stringResource(R.string.trust_certificate_fingerprint_sha1, sha1),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, top = 4.dp),
                    )

                if (sha256 != null)
                    Text(
                        text = stringResource(R.string.trust_certificate_fingerprint_sha256, sha256),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, top = 4.dp),
                    )
            }

            var fingerprintVerified by remember { mutableStateOf(false) }
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Checkbox(
                    checked = fingerprintVerified,
                    onCheckedChange = { fingerprintVerified = it }
                )
                Text(
                    text = stringResource(R.string.trust_certificate_fingerprint_verified),
                    modifier = modifier
                        .clickable {
                            fingerprintVerified = !fingerprintVerified
                        }
                        .weight(1f)
                        .padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = modifier.fillMaxWidth(),
            ) {
                TextButton(
                    enabled = fingerprintVerified,
                    onClick = {
                        onSetTrustDecision(true)
                    },
                    modifier = modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) { Text(stringResource(R.string.trust_certificate_accept).uppercase()) }
                TextButton(
                    onClick = {
                        onSetTrustDecision(false)
                    },
                    modifier = modifier
                        .weight(1f)
                ) { Text(stringResource(R.string.trust_certificate_reject).uppercase()) }
            }
        }
    }
}

@Composable
fun InfoPack(
    @StringRes labelStringRes: Int,
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(labelStringRes).uppercase(),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth(),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    )
}