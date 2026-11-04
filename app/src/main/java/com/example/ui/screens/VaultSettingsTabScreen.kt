package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.SyncModeStatus

@Composable
fun VaultSettingsTabScreen(
    userEmail: String?,
    journalCount: Int,
    tagCount: Int,
    expenseCount: Int,
    clockCount: Int,
    isBiometricEnrolled: Boolean,
    onEnrollBiometricClick: () -> Unit,
    onLockVaultNow: () -> Unit,
    onOpenTagManager: () -> Unit,
    onSignOut: () -> Unit,
    syncStatus: SyncModeStatus = SyncModeStatus.CLOUD_SYNCED,
    isConnectingGoogle: Boolean = false,
    onConnectGoogleForSyncClick: (() -> Unit)? = null
) {
    val isLocalOnly = syncStatus == SyncModeStatus.LOCAL_ONLY_NO_ACCOUNT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.vault_settings_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (isLocalOnly) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Offline Local Vault Active (No Account)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = "Your journal entries, daily gratitude reflections, photos, and expenses are encrypted with AES-256-GCM and stored strictly on this device — zero internet or Google Sign-In required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f)
                    )
                    if (onConnectGoogleForSyncClick != null) {
                        Button(
                            onClick = onConnectGoogleForSyncClick,
                            enabled = !isConnectingGoogle,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("connect_google_sync_button")
                        ) {
                            if (isConnectingGoogle) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.connect_google_sync_btn))
                            }
                        }
                    }
                }
            }
        } else {
            val statusIcon = when (syncStatus) {
                SyncModeStatus.CLOUD_OFFLINE_CACHED -> Icons.Filled.CloudOff
                SyncModeStatus.CLOUD_PENDING_UPLOAD -> Icons.Filled.CloudSync
                else -> Icons.Filled.CloudDone
            }
            val statusHeadline = when (syncStatus) {
                SyncModeStatus.CLOUD_OFFLINE_CACHED -> "Offline • Persistent Disk Cache Active"
                SyncModeStatus.CLOUD_PENDING_UPLOAD -> "Synchronizing Offline Changes to Cloud…"
                else -> "Multi-Device Encrypted Cloud Active"
            }
            val statusDetail = when (syncStatus) {
                SyncModeStatus.CLOUD_OFFLINE_CACHED ->
                    "You are currently offline. Firestore offline persistence keeps all journal entries and expenses accessible and queues new edits to automatically synchronize as soon as connectivity returns."
                SyncModeStatus.CLOUD_PENDING_UPLOAD ->
                    "Connection restored — uploading encrypted offline mutations to your cloud sanctuary."
                else ->
                    "All journal titles, entries, daily gratitude reflections, photo attachments, and expense notes are encrypted locally with AES-256-GCM and cached on-device for instant offline access & automatic synchronization."
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusHeadline,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "Signed in as ${userEmail ?: "Authenticated Google Account"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = statusDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLocalOnly) "On-Device Sanctuary Inventory" else "Synced Sanctuary Inventory",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text("• $journalCount Encrypted Journal Entries & Gratitude Reflections", style = MaterialTheme.typography.bodyMedium)
                Text("• $tagCount Custom Journal Tags", style = MaterialTheme.typography.bodyMedium)
                Text("• $expenseCount Encrypted Expense Records", style = MaterialTheme.typography.bodyMedium)
                Text("• $clockCount Multi-Timezone World Clocks", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (isBiometricEnrolled) "• Biometric / Credential Manager Quick Unlock: Enabled"
                    else "• Biometric / Credential Manager Quick Unlock: Not Yet Enrolled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        FilledTonalButton(
            onClick = onEnrollBiometricClick,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("settings_enroll_biometrics_button")
        ) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isBiometricEnrolled) "Re-Sync Biometric / Passkey Unlock"
                else stringResource(id = R.string.enroll_biometric_btn)
            )
        }

        Button(
            onClick = onLockVaultNow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("settings_lock_vault_button")
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.lock_vault_now))
        }

        OutlinedButton(
            onClick = onOpenTagManager,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("settings_manage_tags_button")
        ) {
            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Custom Journal Tags")
        }

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("settings_sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isLocalOnly) stringResource(id = R.string.exit_offline_vault_btn)
                else stringResource(id = R.string.sign_out_btn)
            )
        }
    }
}
