package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalVaultDatabase
import com.example.data.model.ExpenseCategory
import com.example.data.model.MoodType
import com.example.data.repository.LocalRoomVaultRepository
import com.example.data.security.BiometricEnrollmentStatus
import com.example.data.security.VaultCryptoManager
import com.example.data.util.OfflineSessionPreferences
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.PasscodeVaultScreen
import com.example.ui.theme.VaultJournalTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VaultJournal", appName)
    }

    @Test
    fun `auth screen offers both Google Sign-In and Continue Offline without Sign-In`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        var continuedOffline = false

        composeTestRule.setContent {
            VaultJournalTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    onContinueOffline = { continuedOffline = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("continue_offline_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(continuedOffline)
    }

    @Test
    fun `offline local Room vault persists encrypted journal entries and expenses without Google Sign-In`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LocalVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val localRepo = LocalRoomVaultRepository(db.vaultDao())
            val localUid = OfflineSessionPreferences.LOCAL_OFFLINE_USER_ID

            val salt = VaultCryptoManager.generateSaltBase64()
            val key = VaultCryptoManager.deriveAesKey("4826", salt)
            val verifier = VaultCryptoManager.computeVerifierHashBase64(key, salt)
            localRepo.saveSecurityProfile(salt, verifier, VaultCryptoManager.DEFAULT_ITERATIONS)

            val savedProfile = localRepo.getSecurityProfile(localUid)
            assertNotNull(savedProfile)
            assertTrue(VaultCryptoManager.verifyKey(key, savedProfile!!.saltBase64, savedProfile.verifierHashBase64))

            val tagId = localRepo.createTag("Offline Travel", "#E6B34B")
            val encTitle = VaultCryptoManager.encryptText("Cabin Retreat", key)
            val encBody = VaultCryptoManager.encryptText("Journaling offline in the mountains.", key)
            val encGrat = VaultCryptoManager.encryptText("Quiet pine forest air", key)

            localRepo.createJournalEntry(
                encryptedTitle = encTitle,
                encryptedBody = encBody,
                encryptedGratitudeResponse = encGrat,
                gratitudePromptText = "What brought you peace today?",
                mood = MoodType.CALM,
                tagIds = listOf(tagId),
                encryptedPhotoBase64 = "",
                entryDateEpochMs = 1730000000000L
            )

            localRepo.createExpense(
                encryptedTitle = VaultCryptoManager.encryptText("Mountain Tea", key),
                encryptedNotes = VaultCryptoManager.encryptText("Paid cash offline", key),
                amountMinor = 650L,
                currencyCode = "USD",
                category = ExpenseCategory.DINING,
                spentAtEpochMs = 1730000100000L
            )

            val journals = localRepo.observeJournalEntries(localUid).first()
            assertEquals(1, journals.size)
            assertEquals("Cabin Retreat", VaultCryptoManager.decryptText(journals[0].encryptedTitle, key))
            assertEquals(listOf(tagId), journals[0].tagIds)

            val expenses = localRepo.observeExpenses(localUid).first()
            assertEquals(1, expenses.size)
            assertEquals(650L, expenses[0].amountMinor)
            assertEquals("Mountain Tea", VaultCryptoManager.decryptText(expenses[0].encryptedTitle, key))
        } finally {
            db.close()
        }
    }

    @Test
    fun `firestore persistent cache settings build with unlimited cache size`() {
        val persistentCache = PersistentCacheSettings.newBuilder()
            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(persistentCache)
            .build()
        assertTrue(settings.cacheSettings is PersistentCacheSettings)
    }

    @Test
    fun `biometric and numeric PIN screen unlocks with tactile keypad and triggers biometric prompt`() {
        var submittedPin = ""
        var biometricTriggeredCount = 0

        composeTestRule.setContent {
            VaultJournalTheme {
                PasscodeVaultScreen(
                    isSetupMode = false,
                    userEmail = "user@vaultjournal.test",
                    errorMessage = null,
                    isBusy = false,
                    biometricStatus = BiometricEnrollmentStatus(isPasskeyEnrolled = false),
                    onSetupPasscode = { _, _, _ -> },
                    onUnlockPasscode = { pin, _ -> submittedPin = pin },
                    onBiometricUnlockClick = { biometricTriggeredCount++ },
                    onSignOut = {}
                )
            }
        }

        // Verify Biometric Credential Manager button is displayed and interactive
        composeTestRule.onNodeWithTag("biometric_credential_unlock_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, biometricTriggeredCount)

        // Initially submit button is disabled until at least 4 numeric PIN digits are entered
        composeTestRule.onNodeWithTag("vault_submit_passcode_button")
            .performScrollTo()
            .assertIsNotEnabled()

        // Tap 4 digits on the tactile numeric PIN keypad: 2, 5, 8, 0
        composeTestRule.onNodeWithTag("pin_key_2").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pin_key_5").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pin_key_8").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pin_key_0").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("vault_submit_passcode_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals("2580", submittedPin)
        assertTrue(biometricTriggeredCount >= 1)
    }
}
