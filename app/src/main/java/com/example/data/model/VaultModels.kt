package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MoodType(val label: String, val emoji: String, val score: Int, val accentHex: Long) {
    RADIANT("Radiant", "✨", 5, 0xFFE6B34B),
    GRATEFUL("Grateful", "🌿", 5, 0xFF2D6A4F),
    CALM("Calm", "🌊", 4, 0xFF457B9D),
    REFLECTIVE("Reflective", "🌙", 3, 0xFF7B6D8D),
    NEUTRAL("Neutral", "☁️", 3, 0xFF8D99AE),
    STRESSED("Stressed", "🔥", 2, 0xFFD96B43);

    companion object {
        fun fromKey(key: String): MoodType = entries.firstOrNull { it.name == key } ?: CALM
    }
}

enum class ExpenseCategory(val label: String, val accentHex: Long) {
    DINING("Dining & Cafe", 0xFFE07A5F),
    GROCERIES("Groceries", 0xFF2D6A4F),
    TRANSPORT("Transport", 0xFF457B9D),
    WELLNESS("Health & Wellness", 0xFF81B29A),
    SHOPPING("Shopping", 0xFFD4A373),
    BILLS("Bills & Utilities", 0xFF6D597A),
    TRAVEL("Travel", 0xFFE6B34B),
    OTHER("Other", 0xFF8D99AE);

    companion object {
        fun fromKey(key: String): ExpenseCategory = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

@IgnoreExtraProperties
data class VaultSecurityProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val saltBase64: String = "",
    val verifierHashBase64: String = "",
    val iterations: Long = 65536L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class JournalTag(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#2D6A4F",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class JournalEntryDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val encryptedTitle: String = "",
    val encryptedBody: String = "",
    val encryptedGratitudeResponse: String = "",
    val gratitudePromptText: String = "",
    val mood: String = MoodType.CALM.name,
    val tagIds: List<String> = emptyList(),
    val encryptedPhotoBase64: String = "",
    val entryDateEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedJournalEntry(
    val id: String,
    val title: String,
    val body: String,
    val gratitudeResponse: String,
    val gratitudePromptText: String,
    val mood: MoodType,
    val tagIds: List<String>,
    val photoBytes: ByteArray?,
    val encryptedPhotoBase64: String,
    val entryDateEpochMs: Long,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?
)

@IgnoreExtraProperties
data class ExpenseItemDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val encryptedTitle: String = "",
    val encryptedNotes: String = "",
    val amountMinor: Long = 0L,
    val currencyCode: String = "USD",
    val category: String = ExpenseCategory.DINING.name,
    val spentAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedExpenseItem(
    val id: String,
    val title: String,
    val notes: String,
    val amountMinor: Long,
    val currencyCode: String,
    val category: ExpenseCategory,
    val spentAtEpochMs: Long,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?
)

@IgnoreExtraProperties
data class WorldClockItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val zoneId: String = "UTC",
    val cityLabel: String = "UTC",
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
