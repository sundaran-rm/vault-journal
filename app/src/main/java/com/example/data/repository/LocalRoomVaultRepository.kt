package com.example.data.repository

import android.util.Log
import com.example.data.local.LocalClockEntity
import com.example.data.local.LocalExpenseEntity
import com.example.data.local.LocalJournalEntryEntity
import com.example.data.local.LocalSecurityProfileEntity
import com.example.data.local.LocalTagEntity
import com.example.data.local.LocalVaultDao
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseItemDocument
import com.example.data.model.JournalEntryDocument
import com.example.data.model.JournalTag
import com.example.data.model.MoodType
import com.example.data.model.VaultSecurityProfile
import com.example.data.model.WorldClockItem
import com.example.data.security.VaultCryptoManager
import com.example.data.util.OfflineSessionPreferences
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * On-device Room implementation of [VaultDataSource] enabling full journal, gratitude, tag,
 * photo, expense, and world-clock functionality completely offline without Google Sign-In.
 * Never touches Firebase Auth or FirebaseFirestore.
 */
class LocalRoomVaultRepository(
    private val dao: LocalVaultDao
) : VaultDataSource {

    override val isCloudBacked: Boolean = false

    override fun observeHasPendingSyncWrites(targetUserId: String): Flow<Boolean> = flowOf(false)

    override fun observeSecurityProfile(targetUserId: String): Flow<VaultSecurityProfile?> {
        return dao.observeSecurityProfile().map { entity ->
            entity?.toDomain(targetUserId)
        }
    }

    override suspend fun getSecurityProfile(targetUserId: String): VaultSecurityProfile? {
        return dao.getSecurityProfileOnce()?.toDomain(targetUserId)
    }

    override suspend fun saveSecurityProfile(
        saltBase64: String,
        verifierHashBase64: String,
        iterations: Int,
        existingProfile: VaultSecurityProfile?
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.getSecurityProfileOnce()
        dao.upsertSecurityProfile(
            LocalSecurityProfileEntity(
                id = "vault_config",
                saltBase64 = saltBase64,
                verifierHashBase64 = verifierHashBase64,
                iterations = iterations.toLong(),
                createdAtMs = existing?.createdAtMs ?: now,
                updatedAtMs = now
            )
        )
    }

    override fun observeTags(targetUserId: String): Flow<List<JournalTag>> {
        return dao.observeTags().map { list ->
            list.map { it.toDomain(targetUserId) }
        }
    }

    override suspend fun createTag(name: String, colorHex: String): String {
        val tagId = "tag_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = System.currentTimeMillis()
        dao.upsertTag(
            LocalTagEntity(
                id = tagId,
                name = name.trim().take(40),
                colorHex = colorHex,
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        return tagId
    }

    override suspend fun updateTag(tagId: String, name: String, colorHex: String) {
        val existing = dao.getTagById(tagId)
        val now = System.currentTimeMillis()
        dao.upsertTag(
            LocalTagEntity(
                id = tagId,
                name = name.trim().take(40),
                colorHex = colorHex,
                createdAtMs = existing?.createdAtMs ?: now,
                updatedAtMs = now
            )
        )
    }

    override suspend fun deleteTag(tagId: String) {
        dao.deleteTagById(tagId)
    }

    override fun observeJournalEntries(targetUserId: String): Flow<List<JournalEntryDocument>> {
        return dao.observeJournalEntries().map { list ->
            list.map { it.toDomain(targetUserId) }
        }
    }

    override suspend fun getJournalEntryById(targetUserId: String, entryId: String): JournalEntryDocument? {
        return dao.getJournalById(entryId)?.toDomain(targetUserId)
    }

    override suspend fun createJournalEntry(
        encryptedTitle: String,
        encryptedBody: String,
        encryptedGratitudeResponse: String,
        gratitudePromptText: String,
        mood: MoodType,
        tagIds: List<String>,
        encryptedPhotoBase64: String,
        entryDateEpochMs: Long
    ): String {
        val entryId = "jrn_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = System.currentTimeMillis()
        dao.upsertJournal(
            LocalJournalEntryEntity(
                id = entryId,
                encryptedTitle = encryptedTitle,
                encryptedBody = encryptedBody,
                encryptedGratitudeResponse = encryptedGratitudeResponse,
                gratitudePromptText = gratitudePromptText.take(500),
                mood = mood.name,
                tagIdsJson = encodeTagIds(tagIds.distinct().take(20)),
                encryptedPhotoBase64 = encryptedPhotoBase64,
                entryDateEpochMs = entryDateEpochMs,
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        return entryId
    }

    override suspend fun updateJournalEntry(
        entryId: String,
        encryptedTitle: String,
        encryptedBody: String,
        encryptedGratitudeResponse: String,
        gratitudePromptText: String,
        mood: MoodType,
        tagIds: List<String>,
        encryptedPhotoBase64: String,
        entryDateEpochMs: Long
    ) {
        val existing = dao.getJournalById(entryId)
        val now = System.currentTimeMillis()
        dao.upsertJournal(
            LocalJournalEntryEntity(
                id = entryId,
                encryptedTitle = encryptedTitle,
                encryptedBody = encryptedBody,
                encryptedGratitudeResponse = encryptedGratitudeResponse,
                gratitudePromptText = gratitudePromptText.take(500),
                mood = mood.name,
                tagIdsJson = encodeTagIds(tagIds.distinct().take(20)),
                encryptedPhotoBase64 = encryptedPhotoBase64,
                entryDateEpochMs = entryDateEpochMs,
                createdAtMs = existing?.createdAtMs ?: now,
                updatedAtMs = now
            )
        )
    }

    override suspend fun deleteJournalEntry(entryId: String) {
        dao.deleteJournalById(entryId)
    }

    override fun observeExpenses(targetUserId: String): Flow<List<ExpenseItemDocument>> {
        return dao.observeExpenses().map { list ->
            list.map { it.toDomain(targetUserId) }
        }
    }

    override suspend fun createExpense(
        encryptedTitle: String,
        encryptedNotes: String,
        amountMinor: Long,
        currencyCode: String,
        category: ExpenseCategory,
        spentAtEpochMs: Long
    ): String {
        val expenseId = "exp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = System.currentTimeMillis()
        dao.upsertExpense(
            LocalExpenseEntity(
                id = expenseId,
                encryptedTitle = encryptedTitle,
                encryptedNotes = encryptedNotes,
                amountMinor = amountMinor,
                currencyCode = currencyCode.uppercase().take(3),
                category = category.name,
                spentAtEpochMs = spentAtEpochMs,
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        return expenseId
    }

    override suspend fun deleteExpense(expenseId: String) {
        dao.deleteExpenseById(expenseId)
    }

    override fun observeClocks(targetUserId: String): Flow<List<WorldClockItem>> {
        return dao.observeClocks().map { list ->
            list.map { it.toDomain(targetUserId) }
        }
    }

    override suspend fun createClock(zoneId: String, cityLabel: String, sortOrder: Int): String {
        val clockId = "clk_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = System.currentTimeMillis()
        dao.upsertClock(
            LocalClockEntity(
                id = clockId,
                zoneId = zoneId.take(100),
                cityLabel = cityLabel.take(80),
                sortOrder = sortOrder.coerceIn(0, 1000).toLong(),
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        return clockId
    }

    override suspend fun deleteClock(clockId: String) {
        dao.deleteClockById(clockId)
    }

    companion object {
        internal fun encodeTagIds(tagIds: List<String>): String {
            val arr = JSONArray()
            tagIds.forEach { arr.put(it) }
            return arr.toString()
        }

        internal fun decodeTagIds(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i)
                        if (s.isNotBlank()) add(s)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

private fun LocalSecurityProfileEntity.toDomain(userId: String) = VaultSecurityProfile(
    id = id,
    userId = userId.ifBlank { OfflineSessionPreferences.LOCAL_OFFLINE_USER_ID },
    saltBase64 = saltBase64,
    verifierHashBase64 = verifierHashBase64,
    iterations = iterations,
    createdAt = Timestamp(Date(createdAtMs)),
    updatedAt = Timestamp(Date(updatedAtMs))
)

private fun LocalTagEntity.toDomain(userId: String) = JournalTag(
    id = id,
    userId = userId,
    name = name,
    colorHex = colorHex,
    createdAt = Timestamp(Date(createdAtMs)),
    updatedAt = Timestamp(Date(updatedAtMs))
)

private fun LocalJournalEntryEntity.toDomain(userId: String) = JournalEntryDocument(
    id = id,
    userId = userId,
    encryptedTitle = encryptedTitle,
    encryptedBody = encryptedBody,
    encryptedGratitudeResponse = encryptedGratitudeResponse,
    gratitudePromptText = gratitudePromptText,
    mood = mood,
    tagIds = LocalRoomVaultRepository.decodeTagIds(tagIdsJson),
    encryptedPhotoBase64 = encryptedPhotoBase64,
    entryDateEpochMs = entryDateEpochMs,
    createdAt = Timestamp(Date(createdAtMs)),
    updatedAt = Timestamp(Date(updatedAtMs))
)

private fun LocalExpenseEntity.toDomain(userId: String) = ExpenseItemDocument(
    id = id,
    userId = userId,
    encryptedTitle = encryptedTitle,
    encryptedNotes = encryptedNotes,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    category = category,
    spentAtEpochMs = spentAtEpochMs,
    createdAt = Timestamp(Date(createdAtMs)),
    updatedAt = Timestamp(Date(updatedAtMs))
)

private fun LocalClockEntity.toDomain(userId: String) = WorldClockItem(
    id = id,
    userId = userId,
    zoneId = zoneId,
    cityLabel = cityLabel,
    sortOrder = sortOrder,
    createdAt = Timestamp(Date(createdAtMs)),
    updatedAt = Timestamp(Date(updatedAtMs))
)

/**
 * Seamlessly migrates encrypted journal entries, expenses, tags, clocks, and vault profile
 * authored in Offline Local Mode into the user's authenticated Firestore vault when they
 * connect Google Sign-In.
 */
object LocalVaultSyncBridge {

    suspend fun hasPendingLocalData(dao: LocalVaultDao): Boolean {
        return dao.getAllJournalsOnce().isNotEmpty() ||
            dao.getAllExpensesOnce().isNotEmpty() ||
            dao.getSecurityProfileOnce() != null
    }

    /**
     * Migrates local Room data to [cloudRepo] for [authenticatedUid].
     * - If the cloud account has NO `VaultSecurityProfile` yet, copies the local salt & verifier
     *   directly so existing ciphertexts remain valid with the user's same PIN!
     * - If the cloud account ALREADY has a `VaultSecurityProfile` and [unlockedCloudKey] (plus optional
     *   [localKey]) is supplied, re-encrypts or copies records cleanly.
     */
    suspend fun migrateLocalVaultToCloudIfEligible(
        dao: LocalVaultDao,
        cloudRepo: VaultRepository,
        authenticatedUid: String,
        localAesKey: SecretKey? = null,
        cloudAesKey: SecretKey? = null
    ): Int = withContext(Dispatchers.IO) {
        val localProfile = dao.getSecurityProfileOnce() ?: return@withContext 0
        val localJournals = dao.getAllJournalsOnce()
        val localExpenses = dao.getAllExpensesOnce()
        val localTags = dao.getAllTagsOnce()
        val localClocks = dao.getAllClocksOnce()

        try {
            val cloudProfile = cloudRepo.getSecurityProfile(authenticatedUid)

            val sameCipherSpace: Boolean
            var sourceKey: SecretKey? = localAesKey
            var targetKey: SecretKey? = cloudAesKey

            if (cloudProfile == null) {
                // Cloud account is brand new: adopt local security profile directly!
                cloudRepo.saveSecurityProfile(
                    saltBase64 = localProfile.saltBase64,
                    verifierHashBase64 = localProfile.verifierHashBase64,
                    iterations = localProfile.iterations.toInt(),
                    existingProfile = null
                )
                sameCipherSpace = true
            } else if (
                cloudProfile.saltBase64 == localProfile.saltBase64 &&
                cloudProfile.verifierHashBase64 == localProfile.verifierHashBase64
            ) {
                sameCipherSpace = true
            } else if (cloudAesKey != null && VaultCryptoManager.verifyKey(
                    cloudAesKey,
                    localProfile.saltBase64,
                    localProfile.verifierHashBase64
                )
            ) {
                sameCipherSpace = true
            } else if (localAesKey != null && cloudAesKey != null) {
                sameCipherSpace = false
                sourceKey = localAesKey
                targetKey = cloudAesKey
            } else {
                // Cloud already has a different PIN profile and we don't have both keys yet; keep local safe
                return@withContext 0
            }

            // Map tag IDs so entries preserve tag associations
            val tagIdRemap = mutableMapOf<String, String>()
            for (tag in localTags) {
                val newTagId = cloudRepo.createTag(tag.name, tag.colorHex)
                tagIdRemap[tag.id] = newTagId
            }

            var migratedCount = 0

            for (entry in localJournals) {
                val mappedTagIds = LocalRoomVaultRepository.decodeTagIds(entry.tagIdsJson)
                    .map { tagIdRemap[it] ?: it }
                val encTitle: String
                val encBody: String
                val encGratitude: String
                val encPhoto: String

                if (sameCipherSpace) {
                    encTitle = entry.encryptedTitle
                    encBody = entry.encryptedBody
                    encGratitude = entry.encryptedGratitudeResponse
                    encPhoto = entry.encryptedPhotoBase64
                } else {
                    val sKey = sourceKey ?: continue
                    val tKey = targetKey ?: continue
                    val plainTitle = VaultCryptoManager.decryptText(entry.encryptedTitle, sKey)
                    val plainBody = VaultCryptoManager.decryptText(entry.encryptedBody, sKey)
                    val plainGrat = VaultCryptoManager.decryptText(entry.encryptedGratitudeResponse, sKey)
                    val plainPhoto = VaultCryptoManager.decryptBytesFromBase64(entry.encryptedPhotoBase64, sKey)

                    encTitle = VaultCryptoManager.encryptText(plainTitle.ifBlank { "Untitled Reflection" }, tKey)
                    encBody = VaultCryptoManager.encryptText(plainBody.ifBlank { "Moment captured." }, tKey)
                    encGratitude = if (plainGrat.isBlank()) "" else VaultCryptoManager.encryptText(plainGrat, tKey)
                    encPhoto = if (plainPhoto != null && plainPhoto.isNotEmpty()) {
                        VaultCryptoManager.encryptBytesToBase64(plainPhoto, tKey)
                    } else {
                        ""
                    }
                }

                cloudRepo.createJournalEntry(
                    encryptedTitle = encTitle,
                    encryptedBody = encBody,
                    encryptedGratitudeResponse = encGratitude,
                    gratitudePromptText = entry.gratitudePromptText,
                    mood = MoodType.fromKey(entry.mood),
                    tagIds = mappedTagIds,
                    encryptedPhotoBase64 = encPhoto,
                    entryDateEpochMs = entry.entryDateEpochMs
                )
                migratedCount++
            }

            for (exp in localExpenses) {
                val encTitle: String
                val encNotes: String
                if (sameCipherSpace) {
                    encTitle = exp.encryptedTitle
                    encNotes = exp.encryptedNotes
                } else {
                    val sKey = sourceKey ?: continue
                    val tKey = targetKey ?: continue
                    val plainTitle = VaultCryptoManager.decryptText(exp.encryptedTitle, sKey)
                    val plainNotes = VaultCryptoManager.decryptText(exp.encryptedNotes, sKey)
                    encTitle = VaultCryptoManager.encryptText(plainTitle.ifBlank { "Expense" }, tKey)
                    encNotes = if (plainNotes.isBlank()) "" else VaultCryptoManager.encryptText(plainNotes, tKey)
                }

                cloudRepo.createExpense(
                    encryptedTitle = encTitle,
                    encryptedNotes = encNotes,
                    amountMinor = exp.amountMinor,
                    currencyCode = exp.currencyCode,
                    category = ExpenseCategory.fromKey(exp.category),
                    spentAtEpochMs = exp.spentAtEpochMs
                )
                migratedCount++
            }

            for (clk in localClocks) {
                cloudRepo.createClock(clk.zoneId, clk.cityLabel, clk.sortOrder.toInt())
            }

            // Clear migrated local items so they are not uploaded twice
            dao.clearAllJournals()
            dao.clearAllExpenses()
            dao.clearAllTags()
            dao.clearAllClocks()
            dao.clearSecurityProfile()

            migratedCount
        } catch (e: Exception) {
            Log.w("LocalVaultSyncBridge", "Deferred local-to-cloud migration: ${e.message}", e)
            0
        }
    }
}
