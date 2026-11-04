package com.example

import com.example.data.security.VaultCryptoManager
import com.example.data.util.GratitudePromptProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExampleUnitTest {

    @Test
    fun dailyGratitudePrompt_changesDailyAndRemainsDeterministicForSameDay() {
        val day1 = LocalDate.of(2026, 11, 4)
        val day2 = LocalDate.of(2026, 11, 5)
        val day3 = LocalDate.of(2026, 11, 6)

        val promptDay1First = GratitudePromptProvider.getPromptForDate(day1)
        val promptDay1Second = GratitudePromptProvider.getPromptForDate(day1)
        val promptDay2 = GratitudePromptProvider.getPromptForDate(day2)
        val promptDay3 = GratitudePromptProvider.getPromptForDate(day3)

        assertTrue(promptDay1First.isNotBlank())
        assertEquals(promptDay1First, promptDay1Second)
        assertNotEquals("Prompt must change daily", promptDay1First, promptDay2)
        assertNotEquals("Prompt must change on next day", promptDay2, promptDay3)
    }

    @Test
    fun vaultCryptoManager_encryptsAndDecryptsTextAndBytesAndVerifiesPasscode() {
        val salt = VaultCryptoManager.generateSaltBase64()
        val key = VaultCryptoManager.deriveAesKey("7391", salt, 10000)
        val wrongKey = VaultCryptoManager.deriveAesKey("0000", salt, 10000)

        val verifier = VaultCryptoManager.computeVerifierHashBase64(key, salt)
        assertTrue(VaultCryptoManager.verifyKey(key, salt, verifier))
        assertFalse(VaultCryptoManager.verifyKey(wrongKey, salt, verifier))

        val secretNote = "Grateful for morning coffee & quiet sunlight."
        val encrypted = VaultCryptoManager.encryptText(secretNote, key)
        assertNotEquals(secretNote, encrypted)
        assertEquals(secretNote, VaultCryptoManager.decryptText(encrypted, key))
        assertEquals("", VaultCryptoManager.decryptText(encrypted, wrongKey))

        val rawPhoto = byteArrayOf(1, 2, 3, 4, 5, 99, 120)
        val encPhoto = VaultCryptoManager.encryptBytesToBase64(rawPhoto, key)
        val decPhoto = VaultCryptoManager.decryptBytesFromBase64(encPhoto, key)
        assertTrue(rawPhoto.contentEquals(decPhoto))
    }
}
