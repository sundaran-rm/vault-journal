package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ExpenseCategory
import com.example.data.model.MoodType
import com.example.data.security.VaultCryptoManager
import com.example.data.util.GratitudePromptProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VaultRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createTagAndJournalWithGratitudeAndTags_authenticatedOwner_roundTripsEncryptedData() = runBlocking {
        val aliceUid = signInTestUser("alice@vaultjournal.test")
        val repo = VaultRepository(firestore) { auth.currentUser?.uid }

        // 1. Setup passcode encryption key
        val salt = VaultCryptoManager.generateSaltBase64()
        val key = VaultCryptoManager.deriveAesKey("4829", salt, 12000)
        val verifier = VaultCryptoManager.computeVerifierHashBase64(key, salt)
        withTimeout(5000L) {
            repo.saveSecurityProfile(salt, verifier, 12000, null)
        }

        val loadedProfile = withTimeout(5000L) { repo.getSecurityProfile(aliceUid) }
        assertNotNull(loadedProfile)
        assertTrue(VaultCryptoManager.verifyKey(key, loadedProfile!!.saltBase64, loadedProfile.verifierHashBase64))

        // 2. Create custom tags
        val workTagId = withTimeout(5000L) { repo.createTag("Work", "#2D6A4F") }
        val travelTagId = withTimeout(5000L) { repo.createTag("Travel", "#E6B34B") }

        val tags = withTimeout(4000L) {
            repo.observeTags(aliceUid).first { list -> list.any { it.id == workTagId } }
        }
        assertTrue(tags.any { it.name == "Work" })

        // 3. Create encrypted journal entry with daily gratitude prompt & multiple tags
        val prompt = GratitudePromptProvider.getPromptForDate(LocalDate.of(2026, 11, 4))
        val encTitle = VaultCryptoManager.encryptText("Autumn Sanctuary Notes", key)
        val encBody = VaultCryptoManager.encryptText("Quiet evening tea and planning winter trip.", key)
        val encGratitude = VaultCryptoManager.encryptText("Warm sunlight on my desk.", key)

        val entryId = withTimeout(5000L) {
            repo.createJournalEntry(
                encryptedTitle = encTitle,
                encryptedBody = encBody,
                encryptedGratitudeResponse = encGratitude,
                gratitudePromptText = prompt,
                mood = MoodType.GRATEFUL,
                tagIds = listOf(workTagId, travelTagId),
                encryptedPhotoBase64 = "",
                entryDateEpochMs = 1793808000000L
            )
        }

        val emittedEntries = withTimeout(4000L) {
            repo.observeJournalEntries(aliceUid).first { list -> list.any { it.id == entryId } }
        }
        val stored = emittedEntries.first { it.id == entryId }
        assertEquals("Autumn Sanctuary Notes", VaultCryptoManager.decryptText(stored.encryptedTitle, key))
        assertEquals("Warm sunlight on my desk.", VaultCryptoManager.decryptText(stored.encryptedGratitudeResponse, key))
        assertEquals(listOf(workTagId, travelTagId), stored.tagIds)

        // 4. Create expense & world clock
        val expId = withTimeout(5000L) {
            repo.createExpense(
                encryptedTitle = VaultCryptoManager.encryptText("Artisan Matcha", key),
                encryptedNotes = VaultCryptoManager.encryptText("Morning cafe stop", key),
                amountMinor = 725L,
                currencyCode = "USD",
                category = ExpenseCategory.DINING,
                spentAtEpochMs = 1793808000000L
            )
        }
        val expenses = withTimeout(4000L) {
            repo.observeExpenses(aliceUid).first { list -> list.any { it.id == expId } }
        }
        assertEquals(725L, expenses.first { it.id == expId }.amountMinor)

        val clockId = withTimeout(5000L) {
            repo.createClock("Asia/Tokyo", "Tokyo", 1)
        }
        val clocks = withTimeout(4000L) {
            repo.observeClocks(aliceUid).first { list -> list.any { it.id == clockId } }
        }
        assertTrue(clocks.any { it.cityLabel == "Tokyo" })
    }

    @Test
    fun crossUserAccess_bobCannotReadAliceJournalEntry_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice2@vaultjournal.test")
        val aliceRepo = VaultRepository(firestore) { auth.currentUser?.uid }
        val entryId = withTimeout(5000L) {
            aliceRepo.createJournalEntry(
                encryptedTitle = "iv:secret",
                encryptedBody = "iv:body",
                encryptedGratitudeResponse = "",
                gratitudePromptText = "What brought you joy?",
                mood = MoodType.CALM,
                tagIds = emptyList(),
                encryptedPhotoBase64 = "",
                entryDateEpochMs = 1793808000000L
            )
        }

        signInTestUser("bob2@vaultjournal.test")
        val bobRepo = VaultRepository(firestore) { auth.currentUser?.uid }
        var caught: FirebaseFirestoreException? = null
        try {
            withTimeout(5000L) {
                bobRepo.getJournalEntryById(aliceUid, entryId)
            }
        } catch (e: Exception) {
            caught = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED for cross-user access", caught)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    @Test
    fun unauthenticatedCaller_observeJournals_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice3@vaultjournal.test")
        auth.signOut()
        val unauthRepo = VaultRepository(firestore) { auth.currentUser?.uid }

        var caught: FirebaseFirestoreException? = null
        try {
            withTimeout(4000L) {
                unauthRepo.observeJournalEntries(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED when signed out", caught)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    @Test
    fun offlineQueuedWrite_returnsImmediatelyAndSynchronizesToFirestore() = runBlocking {
        val aliceUid = signInTestUser("alice_offline@vaultjournal.test")
        // Simulate offline connectivity flag so awaitWriteOrQueueOffline returns via local cache queue
        val offlineAwareRepo = VaultRepository(
            db = firestore,
            uidProvider = { auth.currentUser?.uid },
            isOnlineProvider = { false }
        )

        val key = VaultCryptoManager.deriveAesKey("7391", VaultCryptoManager.generateSaltBase64(), 12000)
        val encTitle = VaultCryptoManager.encryptText("Offline Subway Note", key)
        val encBody = VaultCryptoManager.encryptText("Written while offline; auto-syncs when connected.", key)

        val entryId = withTimeout(3000L) {
            offlineAwareRepo.createJournalEntry(
                encryptedTitle = encTitle,
                encryptedBody = encBody,
                encryptedGratitudeResponse = "",
                gratitudePromptText = "What made you smile today?",
                mood = MoodType.RADIANT,
                tagIds = emptyList(),
                encryptedPhotoBase64 = "",
                entryDateEpochMs = 1793810000000L
            )
        }

        val expId = withTimeout(3000L) {
            offlineAwareRepo.createExpense(
                encryptedTitle = VaultCryptoManager.encryptText("Metro Ticket", key),
                encryptedNotes = "",
                amountMinor = 290L,
                currencyCode = "USD",
                category = ExpenseCategory.TRANSPORT,
                spentAtEpochMs = 1793810000000L
            )
        }

        val entries = withTimeout(4000L) {
            offlineAwareRepo.observeJournalEntries(aliceUid).first { list -> list.any { it.id == entryId } }
        }
        assertEquals("Offline Subway Note", VaultCryptoManager.decryptText(entries.first { it.id == entryId }.encryptedTitle, key))

        val expenses = withTimeout(4000L) {
            offlineAwareRepo.observeExpenses(aliceUid).first { list -> list.any { it.id == expId } }
        }
        assertEquals(290L, expenses.first { it.id == expId }.amountMinor)
    }
}
