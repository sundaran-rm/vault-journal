package com.example.data.security

import android.app.Activity
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.vaultBiometricDataStore by preferencesDataStore(name = "vault_biometric_cred_prefs")

data class BiometricEnrollmentStatus(
    val isPasskeyEnrolled: Boolean = false,
    val isCredentialSaved: Boolean = false,
    val hasHardwareWrappedKey: Boolean = false
) {
    val isBiometricQuickUnlockReady: Boolean
        get() = hasHardwareWrappedKey || isPasskeyEnrolled || isCredentialSaved
}

/**
 * Integrates Jetpack Android Credential Manager (`androidx.credentials`) for journal access:
 * 1. Biometric Passkey registration & assertion (`CreatePublicKeyCredentialRequest` / `GetPublicKeyCredentialOption`)
 *    paired with an Android Keystore hardware-wrapped AES session key so biometric verification unlocks
 *    the encrypted journal immediately without typing the numeric PIN.
 * 2. Credential Manager Password/PIN provider storage & biometric-gated autofill (`CreatePasswordRequest` /
 *    `GetPasswordOption`) so users can also save or retrieve their numeric PIN via their Android Credential Provider.
 */
object VaultCredentialAccessHelper {
    private const val TAG = "VaultCredAccess"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS_PREFIX = "vaultjournal_wrap_key_"
    private const val RP_ID = "vaultjournal.aistudio.app"

    private fun wrappedKeyPref(uid: String) = stringPreferencesKey("wrapped_aes_key_$uid")
    private fun passkeyIdPref(uid: String) = stringPreferencesKey("passkey_cred_id_$uid")
    private fun passwordSavedPref(uid: String) = stringPreferencesKey("password_saved_$uid")

    fun observeBiometricStatus(context: Context, userId: String): Flow<BiometricEnrollmentStatus> {
        return context.applicationContext.vaultBiometricDataStore.data.map { prefs ->
            BiometricEnrollmentStatus(
                isPasskeyEnrolled = !prefs[passkeyIdPref(userId)].isNullOrBlank(),
                isCredentialSaved = prefs[passwordSavedPref(userId)] == "true",
                hasHardwareWrappedKey = !prefs[wrappedKeyPref(userId)].isNullOrBlank()
            )
        }
    }

    private fun getOrCreateHardwareWrappingKey(userId: String): SecretKey {
        val alias = "$KEYSTORE_ALIAS_PREFIX${userId.take(24)}"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    suspend fun cacheHardwareWrappedVaultKey(context: Context, userId: String, vaultAesKey: SecretKey) {
        try {
            val wrapKey = getOrCreateHardwareWrappingKey(userId)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(vaultAesKey.encoded)
            val encoded = "${Base64.getEncoder().encodeToString(iv)}:${Base64.getEncoder().encodeToString(cipherBytes)}"
            context.applicationContext.vaultBiometricDataStore.edit { prefs ->
                prefs[wrappedKeyPref(userId)] = encoded
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware key wrapping unavailable in this environment: ${e.message}")
        }
    }

    private suspend fun unwrapHardwareVaultKey(context: Context, userId: String): SecretKey? {
        return try {
            val prefs = context.applicationContext.vaultBiometricDataStore.data.first()
            val payload = prefs[wrappedKeyPref(userId)] ?: return null
            val idx = payload.indexOf(':')
            if (idx <= 0) return null
            val iv = Base64.getDecoder().decode(payload.substring(0, idx))
            val ct = Base64.getDecoder().decode(payload.substring(idx + 1))
            val wrapKey = getOrCreateHardwareWrappingKey(userId)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
            val rawKeyBytes = cipher.doFinal(ct)
            SecretKeySpec(rawKeyBytes, "AES")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unwrap hardware vault key: ${e.message}")
            null
        }
    }

    /**
     * Saves the numeric PIN in Android Credential Manager (`CreatePasswordRequest`) so the user's
     * biometric-protected credential provider can autofill and unlock the journal across sessions.
     */
    suspend fun registerPinWithCredentialManager(
        activityContext: Context,
        credentialManager: CredentialManager,
        userId: String,
        userEmail: String?,
        numericPin: String
    ): Result<String> {
        val activity = activityContext as? Activity
            ?: return Result.failure(IllegalStateException("Activity context required for Credential Manager"))
        val accountLabel = userEmail?.takeIf { it.isNotBlank() } ?: "vaultjournal-$userId"
        return try {
            val request = CreatePasswordRequest(
                id = accountLabel,
                password = numericPin
            )
            credentialManager.createCredential(activity, request)
            activityContext.applicationContext.vaultBiometricDataStore.edit { prefs ->
                prefs[passwordSavedPref(userId)] = "true"
            }
            Result.success("PIN saved to Android Credential Manager")
        } catch (e: CreateCredentialCancellationException) {
            Log.w(TAG, "User skipped saving PIN to Credential Manager: ${e.message}")
            Result.failure(e)
        } catch (e: CreateCredentialException) {
            Log.w(TAG, "Credential Manager PIN registration failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Enrolls a platform Biometric Passkey via Android Credential Manager (`CreatePublicKeyCredentialRequest`)
     * and seals the derived AES-256 vault key inside Android Keystore.
     */
    suspend fun enrollBiometricPasskey(
        activityContext: Context,
        credentialManager: CredentialManager,
        userId: String,
        userEmail: String?,
        vaultAesKey: SecretKey
    ): Result<String> {
        val activity = activityContext as? Activity
            ?: return Result.failure(IllegalStateException("Activity context required for Passkey enrollment"))

        cacheHardwareWrappedVaultKey(activityContext, userId, vaultAesKey)

        val challengeBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val userIdBytes = userId.toByteArray(Charsets.UTF_8)
        val urlEncoder = Base64.getUrlEncoder().withoutPadding()
        val displayName = userEmail?.takeIf { it.isNotBlank() } ?: "VaultJournal Sanctuary"

        val createRequestJson = JSONObject().apply {
            put("challenge", urlEncoder.encodeToString(challengeBytes))
            put("rp", JSONObject().apply {
                put("name", "VaultJournal")
                put("id", RP_ID)
            })
            put("user", JSONObject().apply {
                put("id", urlEncoder.encodeToString(userIdBytes))
                put("name", displayName)
                put("displayName", displayName)
            })
            put("pubKeyCredParams", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -7) // ES256
                })
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -257) // RS256
                })
            })
            put("timeout", 60000)
            put("attestation", "none")
            put("authenticatorSelection", JSONObject().apply {
                put("authenticatorAttachment", "platform")
                put("residentKey", "required")
                put("userVerification", "required")
            })
        }.toString()

        return try {
            val request = CreatePublicKeyCredentialRequest(requestJson = createRequestJson)
            val response = credentialManager.createCredential(activity, request)
            val credId = if (response is CreatePublicKeyCredentialResponse) {
                runCatching {
                    JSONObject(response.registrationResponseJson).optString("id", "enrolled")
                }.getOrDefault("enrolled")
            } else {
                "enrolled"
            }
            activityContext.applicationContext.vaultBiometricDataStore.edit { prefs ->
                prefs[passkeyIdPref(userId)] = credId
            }
            Result.success("Biometric Passkey enrolled via Credential Manager")
        } catch (e: CreateCredentialCancellationException) {
            Log.w(TAG, "Biometric Passkey enrollment dismissed: ${e.message}")
            Result.failure(e)
        } catch (e: CreateCredentialException) {
            Log.w(TAG, "Passkey enrollment unavailable (${e.type}), falling back to PIN Credential Manager", e)
            Result.failure(e)
        }
    }

    sealed interface CredentialUnlockOutcome {
        data class UnlockedWithHardwareKey(val aesKey: SecretKey) : CredentialUnlockOutcome
        data class RetrievedNumericPin(val numericPin: String) : CredentialUnlockOutcome
        data object Cancelled : CredentialUnlockOutcome
        data class Unavailable(val reason: String) : CredentialUnlockOutcome
    }

    /**
     * Prompts biometric / passkey / saved PIN authentication via Android Credential Manager (`getCredential`).
     * Supports both `GetPublicKeyCredentialOption` (biometric passkey assertion) and `GetPasswordOption`
     * (biometric-protected PIN credential stored in Credential Manager).
     */
    suspend fun authenticateWithCredentialManager(
        activityContext: Context,
        credentialManager: CredentialManager,
        userId: String
    ): CredentialUnlockOutcome {
        val activity = activityContext as? Activity
            ?: return CredentialUnlockOutcome.Unavailable("Activity context unavailable.")

        val prefs = activityContext.applicationContext.vaultBiometricDataStore.data.first()
        val hasPasskey = !prefs[passkeyIdPref(userId)].isNullOrBlank()
        val challengeBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)

        val passkeyRequestJson = JSONObject().apply {
            put("challenge", challengeB64)
            put("rpId", RP_ID)
            put("timeout", 60000)
            put("userVerification", "required")
        }.toString()

        // Try stored Password/PIN + Passkey via Credential Manager
        val requestBuilder = GetCredentialRequest.Builder()
            .addCredentialOption(GetPasswordOption())
        if (hasPasskey) {
            requestBuilder.addCredentialOption(
                GetPublicKeyCredentialOption(requestJson = passkeyRequestJson)
            )
        }

        return try {
            val result = credentialManager.getCredential(
                context = activity,
                request = requestBuilder.build()
            )
            when (val cred = result.credential) {
                is PublicKeyCredential -> {
                    val unwrapped = unwrapHardwareVaultKey(activityContext, userId)
                    if (unwrapped != null) {
                        CredentialUnlockOutcome.UnlockedWithHardwareKey(unwrapped)
                    } else {
                        CredentialUnlockOutcome.Unavailable("Passkey verified, please enter numeric PIN once to sync key.")
                    }
                }
                is PasswordCredential -> {
                    CredentialUnlockOutcome.RetrievedNumericPin(cred.password)
                }
                else -> CredentialUnlockOutcome.Unavailable("Unsupported credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Biometric / Credential Manager unlock cancelled: ${e.message}")
            CredentialUnlockOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No saved biometric passkey or PIN credential found yet: ${e.message}")
            CredentialUnlockOutcome.Unavailable(
                "No biometric credential saved yet. Unlock once with your numeric PIN and tap 'Save Biometric Unlock'."
            )
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager authentication error: ${e.message}", e)
            CredentialUnlockOutcome.Unavailable(
                e.localizedMessage ?: "Biometric Credential Manager unavailable on this device; use numeric PIN pad."
            )
        }
    }
}
