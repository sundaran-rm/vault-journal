package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.security.BiometricEnrollmentStatus

/**
 * Biometric + Tactile Numeric PIN Authentication Screen for Journal Access,
 * powered by Android Jetpack Credential Manager (`androidx.credentials`).
 */
@Composable
fun PasscodeVaultScreen(
    isSetupMode: Boolean,
    userEmail: String?,
    errorMessage: String?,
    isBusy: Boolean,
    biometricStatus: BiometricEnrollmentStatus,
    onSetupPasscode: (pin: String, confirmPin: String, enrollBiometrics: Boolean) -> Unit,
    onUnlockPasscode: (pin: String, saveToCredentialManager: Boolean) -> Unit,
    onBiometricUnlockClick: () -> Unit,
    onSignOut: () -> Unit,
    isLocalOfflineMode: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var firstSetupPin by rememberSaveable { mutableStateOf("") }
    var isConfirmStep by rememberSaveable { mutableStateOf(false) }
    var enableBiometricCredentialSave by rememberSaveable { mutableStateOf(true) }
    var saveOnUnlockChecked by rememberSaveable { mutableStateOf(false) }

    // Automatically trigger Credential Manager Biometric / Saved Credential prompt once on unlock screen if enrolled
    var autoPromptedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isSetupMode, biometricStatus.isBiometricQuickUnlockReady) {
        if (!isSetupMode && biometricStatus.isBiometricQuickUnlockReady && !autoPromptedOnce) {
            autoPromptedOnce = true
            onBiometricUnlockClick()
        }
    }

    val titleText = when {
        isSetupMode && isConfirmStep -> stringResource(id = R.string.vault_confirm_step_title)
        isSetupMode -> stringResource(id = R.string.vault_setup_title)
        else -> stringResource(id = R.string.vault_unlock_title)
    }

    val subtitleText = when {
        isSetupMode && isConfirmStep -> stringResource(id = R.string.vault_confirm_step_subtitle)
        isSetupMode -> stringResource(id = R.string.vault_setup_subtitle)
        else -> stringResource(id = R.string.vault_unlock_subtitle)
    }

    fun appendDigit(digit: Char) {
        if (!isBusy && enteredPin.length < 8) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            enteredPin += digit
        }
    }

    fun removeLastDigit() {
        if (!isBusy && enteredPin.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            enteredPin = enteredPin.dropLast(1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emblem Header
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSetupMode) Icons.Filled.Key else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            if (!userEmail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isLocalOfflineMode) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(id = R.string.sync_badge_local_only),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Prominent Biometric / Passkey Credential Manager Button (Unlock mode)
            if (!isSetupMode) {
                FilledTonalButton(
                    onClick = onBiometricUnlockClick,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("biometric_credential_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.vault_biometric_btn),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // PIN Dots & Tactile Numeric Keypad Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // PIN Indicator Dots (4 minimum, expands smoothly up to 8)
                    val totalDots = maxOf(4, enteredPin.length)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(28.dp)
                            .semantics {
                                contentDescription = "${enteredPin.length} digits entered"
                            }
                            .testTag("pin_dots_indicator")
                    ) {
                        for (index in 0 until totalDots) {
                            val isFilled = index < enteredPin.length
                            val dotColor by animateColorAsState(
                                targetValue = if (isFilled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                label = "pinDotColor"
                            )
                            Box(
                                modifier = Modifier
                                    .size(if (isFilled) 16.dp else 14.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isFilled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Text(
                        text = "${enteredPin.length} / 8 digits (min 4)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_pin_error_banner")
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // 3x4 Tactile Numeric Keypad
                    val rows = listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9')
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        rows.forEach { rowDigits ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowDigits.forEach { digit ->
                                    NumericPinKeyButton(
                                        label = digit.toString(),
                                        testTag = "pin_key_$digit",
                                        onClick = { appendDigit(digit) }
                                    )
                                }
                            }
                        }

                        // Bottom Row: Biometric/Clear, 0, Backspace
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isSetupMode) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !isBusy) { onBiometricUnlockClick() }
                                        .testTag("pin_pad_biometric_key")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Fingerprint,
                                            contentDescription = "Biometric unlock",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !isBusy && enteredPin.isNotEmpty()) {
                                            enteredPin = ""
                                        }
                                        .testTag("pin_key_clear")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "CLR",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            NumericPinKeyButton(
                                label = "0",
                                testTag = "pin_key_0",
                                onClick = { appendDigit('0') }
                            )

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !isBusy && enteredPin.isNotEmpty()) {
                                        removeLastDigit()
                                    }
                                    .testTag("pin_key_backspace")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete last digit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Biometric / Credential Manager enrollment toggle
                    if (isSetupMode && isConfirmStep) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.vault_save_credential_toggle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = enableBiometricCredentialSave,
                                onCheckedChange = { enableBiometricCredentialSave = it },
                                modifier = Modifier.testTag("toggle_enroll_biometrics_switch")
                            )
                        }
                    } else if (!isSetupMode && !biometricStatus.isBiometricQuickUnlockReady) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Save Biometric / Credential Manager Unlock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = saveOnUnlockChecked,
                                onCheckedChange = { saveOnUnlockChecked = it },
                                modifier = Modifier.testTag("toggle_save_biometric_on_unlock")
                            )
                        }
                    } else if (!isSetupMode && biometricStatus.isBiometricQuickUnlockReady) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Biometric Credential Manager Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Primary Submit / Next Step Action Button
                    Button(
                        onClick = {
                            if (isSetupMode) {
                                if (!isConfirmStep) {
                                    firstSetupPin = enteredPin
                                    enteredPin = ""
                                    isConfirmStep = true
                                } else {
                                    onSetupPasscode(firstSetupPin, enteredPin, enableBiometricCredentialSave)
                                }
                            } else {
                                onUnlockPasscode(enteredPin, saveOnUnlockChecked)
                            }
                        },
                        enabled = !isBusy && enteredPin.length >= 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("vault_submit_passcode_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            val btnText = when {
                                isSetupMode && !isConfirmStep -> stringResource(id = R.string.vault_next_confirm_btn)
                                isSetupMode -> stringResource(id = R.string.vault_create_btn)
                                else -> stringResource(id = R.string.vault_unlock_btn)
                            }
                            Text(
                                text = btnText,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    if (isSetupMode && isConfirmStep) {
                        TextButton(
                            onClick = {
                                isConfirmStep = false
                                firstSetupPin = ""
                                enteredPin = ""
                            },
                            modifier = Modifier.testTag("reset_pin_setup_step_button")
                        ) {
                            Text("Re-enter Initial PIN")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("vault_switch_account_button")
            ) {
                Text(
                    if (isLocalOfflineMode) stringResource(id = R.string.vault_exit_offline_to_auth)
                    else stringResource(id = R.string.vault_sign_out)
                )
            }
        }
    }
}

@Composable
private fun NumericPinKeyButton(
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
