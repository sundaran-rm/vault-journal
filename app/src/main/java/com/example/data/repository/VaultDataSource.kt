package com.example.data.repository

import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseItemDocument
import com.example.data.model.JournalEntryDocument
import com.example.data.model.JournalTag
import com.example.data.model.MoodType
import com.example.data.model.VaultSecurityProfile
import com.example.data.model.WorldClockItem
import kotlinx.coroutines.flow.Flow

/**
 * Unified storage contract supporting both:
 * 1. Cloud-synced + PersistentCache offline Firestore (`VaultRepository`) when signed in with Google
 * 2. Pure on-device Room SQLite (`LocalRoomVaultRepository`) when running offline without Google Sign-In
 */
interface VaultDataSource {
    val isCloudBacked: Boolean

    fun observeHasPendingSyncWrites(targetUserId: String): Flow<Boolean>

    // 1. Security Profile
    fun observeSecurityProfile(targetUserId: String): Flow<VaultSecurityProfile?>
    suspend fun getSecurityProfile(targetUserId: String): VaultSecurityProfile?
    suspend fun saveSecurityProfile(
        saltBase64: String,
        verifierHashBase64: String,
        iterations: Int,
        existingProfile: VaultSecurityProfile? = null
    )

    // 2. Custom Journal Tags
    fun observeTags(targetUserId: String): Flow<List<JournalTag>>
    suspend fun createTag(name: String, colorHex: String): String
    suspend fun updateTag(tagId: String, name: String, colorHex: String)
    suspend fun deleteTag(tagId: String)

    // 3. Encrypted Journal Entries
    fun observeJournalEntries(targetUserId: String): Flow<List<JournalEntryDocument>>
    suspend fun getJournalEntryById(targetUserId: String, entryId: String): JournalEntryDocument?
    suspend fun createJournalEntry(
        encryptedTitle: String,
        encryptedBody: String,
        encryptedGratitudeResponse: String,
        gratitudePromptText: String,
        mood: MoodType,
        tagIds: List<String>,
        encryptedPhotoBase64: String,
        entryDateEpochMs: Long
    ): String

    suspend fun updateJournalEntry(
        entryId: String,
        encryptedTitle: String,
        encryptedBody: String,
        encryptedGratitudeResponse: String,
        gratitudePromptText: String,
        mood: MoodType,
        tagIds: List<String>,
        encryptedPhotoBase64: String,
        entryDateEpochMs: Long
    )

    suspend fun deleteJournalEntry(entryId: String)

    // 4. Encrypted Daily Financial Expenses
    fun observeExpenses(targetUserId: String): Flow<List<ExpenseItemDocument>>
    suspend fun createExpense(
        encryptedTitle: String,
        encryptedNotes: String,
        amountMinor: Long,
        currencyCode: String,
        category: ExpenseCategory,
        spentAtEpochMs: Long
    ): String

    suspend fun deleteExpense(expenseId: String)

    // 5. Multi-Timezone World Clocks
    fun observeClocks(targetUserId: String): Flow<List<WorldClockItem>>
    suspend fun createClock(zoneId: String, cityLabel: String, sortOrder: Int): String
    suspend fun deleteClock(clockId: String)
}
