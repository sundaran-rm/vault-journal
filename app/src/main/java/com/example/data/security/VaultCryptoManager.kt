package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides genuine AES-256-GCM authenticated encryption and PBKDF2WithHmacSHA256 key derivation
 * so journal entries, gratitude reflections, photo attachments, and expense notes are encrypted
 * client-side before syncing to Cloud Firestore across multiple devices.
 */
object VaultCryptoManager {
    const val DEFAULT_ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    fun generateSaltBase64(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun deriveAesKey(passcode: String, saltBase64: String, iterations: Int = DEFAULT_ITERATIONS): SecretKey {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(passcode.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val rawKey = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(rawKey, "AES")
    }

    fun computeVerifierHashBase64(key: SecretKey, saltBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("VAULT_VERIFIER_V1:".toByteArray(Charsets.UTF_8))
        digest.update(Base64.getDecoder().decode(saltBase64))
        val hash = digest.digest(key.encoded)
        return Base64.getEncoder().encodeToString(hash)
    }

    fun verifyKey(key: SecretKey, saltBase64: String, expectedVerifierBase64: String): Boolean {
        val computed = computeVerifierHashBase64(key, saltBase64)
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            expectedVerifierBase64.toByteArray(Charsets.UTF_8)
        )
    }

    fun encryptText(plainText: String, key: SecretKey): String {
        if (plainText.isEmpty()) return ""
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(cipherBytes)
        return "$ivB64:$ctB64"
    }

    fun decryptText(encryptedPayload: String, key: SecretKey): String {
        if (encryptedPayload.isBlank()) return ""
        val separatorIdx = encryptedPayload.indexOf(':')
        if (separatorIdx <= 0) return ""
        return try {
            val ivB64 = encryptedPayload.substring(0, separatorIdx)
            val ctB64 = encryptedPayload.substring(separatorIdx + 1)
            val iv = Base64.getDecoder().decode(ivB64)
            val cipherBytes = Base64.getDecoder().decode(ctB64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun encryptBytesToBase64(rawBytes: ByteArray, key: SecretKey): String {
        if (rawBytes.isEmpty()) return ""
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(rawBytes)
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(cipherBytes)
        return "$ivB64:$ctB64"
    }

    fun decryptBytesFromBase64(encryptedPayload: String, key: SecretKey): ByteArray? {
        if (encryptedPayload.isBlank()) return null
        val separatorIdx = encryptedPayload.indexOf(':')
        if (separatorIdx <= 0) return null
        return try {
            val ivB64 = encryptedPayload.substring(0, separatorIdx)
            val ctB64 = encryptedPayload.substring(separatorIdx + 1)
            val iv = Base64.getDecoder().decode(ivB64)
            val cipherBytes = Base64.getDecoder().decode(ctB64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            null
        }
    }
}
