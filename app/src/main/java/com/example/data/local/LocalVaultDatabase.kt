package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_security_profile")
data class LocalSecurityProfileEntity(
    @PrimaryKey val id: String = "vault_config",
    val saltBase64: String,
    val verifierHashBase64: String,
    val iterations: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "local_tags")
data class LocalTagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "local_journals")
data class LocalJournalEntryEntity(
    @PrimaryKey val id: String,
    val encryptedTitle: String,
    val encryptedBody: String,
    val encryptedGratitudeResponse: String,
    val gratitudePromptText: String,
    val mood: String,
    val tagIdsJson: String,
    val encryptedPhotoBase64: String,
    val entryDateEpochMs: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "local_expenses")
data class LocalExpenseEntity(
    @PrimaryKey val id: String,
    val encryptedTitle: String,
    val encryptedNotes: String,
    val amountMinor: Long,
    val currencyCode: String,
    val category: String,
    val spentAtEpochMs: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "local_clocks")
data class LocalClockEntity(
    @PrimaryKey val id: String,
    val zoneId: String,
    val cityLabel: String,
    val sortOrder: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Dao
interface LocalVaultDao {
    // Security Profile
    @Query("SELECT * FROM local_security_profile WHERE id = 'vault_config' LIMIT 1")
    fun observeSecurityProfile(): Flow<LocalSecurityProfileEntity?>

    @Query("SELECT * FROM local_security_profile WHERE id = 'vault_config' LIMIT 1")
    suspend fun getSecurityProfileOnce(): LocalSecurityProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSecurityProfile(entity: LocalSecurityProfileEntity)

    // Tags
    @Query("SELECT * FROM local_tags ORDER BY LOWER(name) ASC")
    fun observeTags(): Flow<List<LocalTagEntity>>

    @Query("SELECT * FROM local_tags")
    suspend fun getAllTagsOnce(): List<LocalTagEntity>

    @Query("SELECT * FROM local_tags WHERE id = :tagId LIMIT 1")
    suspend fun getTagById(tagId: String): LocalTagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(entity: LocalTagEntity)

    @Query("DELETE FROM local_tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: String)

    // Journals
    @Query("SELECT * FROM local_journals ORDER BY entryDateEpochMs DESC")
    fun observeJournalEntries(): Flow<List<LocalJournalEntryEntity>>

    @Query("SELECT * FROM local_journals ORDER BY entryDateEpochMs DESC")
    suspend fun getAllJournalsOnce(): List<LocalJournalEntryEntity>

    @Query("SELECT * FROM local_journals WHERE id = :entryId LIMIT 1")
    suspend fun getJournalById(entryId: String): LocalJournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJournal(entity: LocalJournalEntryEntity)

    @Query("DELETE FROM local_journals WHERE id = :entryId")
    suspend fun deleteJournalById(entryId: String)

    // Expenses
    @Query("SELECT * FROM local_expenses ORDER BY spentAtEpochMs DESC")
    fun observeExpenses(): Flow<List<LocalExpenseEntity>>

    @Query("SELECT * FROM local_expenses ORDER BY spentAtEpochMs DESC")
    suspend fun getAllExpensesOnce(): List<LocalExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(entity: LocalExpenseEntity)

    @Query("DELETE FROM local_expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String)

    // World Clocks
    @Query("SELECT * FROM local_clocks ORDER BY sortOrder ASC")
    fun observeClocks(): Flow<List<LocalClockEntity>>

    @Query("SELECT * FROM local_clocks ORDER BY sortOrder ASC")
    suspend fun getAllClocksOnce(): List<LocalClockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClock(entity: LocalClockEntity)

    @Query("DELETE FROM local_clocks WHERE id = :clockId")
    suspend fun deleteClockById(clockId: String)

    // Cleanup migrated items
    @Query("DELETE FROM local_journals")
    suspend fun clearAllJournals()

    @Query("DELETE FROM local_expenses")
    suspend fun clearAllExpenses()

    @Query("DELETE FROM local_tags")
    suspend fun clearAllTags()

    @Query("DELETE FROM local_clocks")
    suspend fun clearAllClocks()

    @Query("DELETE FROM local_security_profile")
    suspend fun clearSecurityProfile()
}

@Database(
    entities = [
        LocalSecurityProfileEntity::class,
        LocalTagEntity::class,
        LocalJournalEntryEntity::class,
        LocalExpenseEntity::class,
        LocalClockEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LocalVaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): LocalVaultDao

    companion object {
        @Volatile
        private var INSTANCE: LocalVaultDatabase? = null

        fun getInstance(context: Context): LocalVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalVaultDatabase::class.java,
                    "vault_journal_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
