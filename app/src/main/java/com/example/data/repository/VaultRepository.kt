package com.example.data.repository

import android.content.Context
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseItemDocument
import com.example.data.model.JournalEntryDocument
import com.example.data.model.JournalTag
import com.example.data.model.MoodType
import com.example.data.model.VaultSecurityProfile
import com.example.data.model.WorldClockItem
import com.example.data.util.FirestoreOfflineConfig
import com.example.data.util.NetworkConnectivityObserver
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class VaultRepository(
    private val db: FirebaseFirestore,
    private val uidProvider: () -> String?,
    private val isOnlineProvider: () -> Boolean
) : VaultDataSource {

    constructor(
        db: FirebaseFirestore,
        uidProvider: () -> String? = { Firebase.auth.currentUser?.uid }
    ) : this(
        db = db,
        uidProvider = uidProvider,
        isOnlineProvider = { true }
    )

    constructor(context: Context) : this(
        db = FirestoreOfflineConfig.getConfiguredFirestore(context),
        uidProvider = { Firebase.auth.currentUser?.uid },
        isOnlineProvider = NetworkConnectivityObserver(context)::isCurrentlyOnline
    )

    override val isCloudBacked: Boolean = true

    private fun requireUserId(): String {
        return uidProvider()
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    /**
     * Awaits remote Firestore acknowledgment when online, or returns promptly when offline
     * after Firestore commits the mutation to its local `PersistentCache` write queue for
     * automatic background synchronization when connectivity is restored.
     */
    private suspend fun awaitWriteOrQueueOffline(
        task: Task<Void>,
        opType: OperationType,
        path: String
    ) {
        task.addOnFailureListener { e ->
            handleFirestoreError(e, opType, path)
        }
        if (!isOnlineProvider()) {
            // Offline: Firestore has already written the mutation to PersistentCache and queued it
            // for automatic server sync on reconnect. Give synchronous local errors a brief window.
            withTimeoutOrNull(250L) {
                task.await()
            }
            task.exception?.let { throw it }
            return
        }
        val completed = withTimeoutOrNull(6000L) {
            task.await()
            true
        }
        if (completed == null) {
            // Network dropped mid-flight; mutation remains queued in PersistentCache for auto-sync
            task.exception?.let { throw it }
        }
    }

    /**
     * Reads a document with offline cache resilience while preserving immediate propagation
     * of security rule errors (`PERMISSION_DENIED`).
     */
    private suspend fun getDocumentOfflineAware(docRef: DocumentReference): DocumentSnapshot? {
        if (!isOnlineProvider()) {
            return try {
                docRef.get(Source.CACHE).await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    null
                } else {
                    throw e
                }
            }
        }
        return try {
            docRef.get(Source.DEFAULT).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                try {
                    docRef.get(Source.CACHE).await()
                } catch (_: FirebaseFirestoreException) {
                    null
                }
            } else {
                throw e
            }
        }
    }

    override fun observeHasPendingSyncWrites(targetUserId: String): Flow<Boolean> = flow {
        val journalsFlow = db.collection("users").document(targetUserId).collection("journals")
            .whereEqualTo("userId", targetUserId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap -> snap.metadata.hasPendingWrites() }
            .catch { emit(false) }

        val expensesFlow = db.collection("users").document(targetUserId).collection("expenses")
            .whereEqualTo("userId", targetUserId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap -> snap.metadata.hasPendingWrites() }
            .catch { emit(false) }

        emitAll(
            combine(journalsFlow, expensesFlow) { jPending, ePending ->
                jPending || ePending
            }.distinctUntilChanged()
        )
    }

    // --- 1. Vault Security Profile (/users/{userId}/security/vault_config) ---

    override fun observeSecurityProfile(targetUserId: String): Flow<VaultSecurityProfile?> = flow {
        val docRef = db.collection("users").document(targetUserId)
            .collection("security").document("vault_config")
        emitAll(
            docRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            VaultSecurityProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    override suspend fun getSecurityProfile(targetUserId: String): VaultSecurityProfile? {
        val docRef = db.collection("users").document(targetUserId)
            .collection("security").document("vault_config")
        return try {
            val snap = getDocumentOfflineAware(docRef)
            if (snap != null && snap.exists()) {
                snap.toObject(
                    VaultSecurityProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    override suspend fun saveSecurityProfile(
        saltBase64: String,
        verifierHashBase64: String,
        iterations: Int,
        existingProfile: VaultSecurityProfile?
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("security").document("vault_config")
        try {
            val resolvedExisting = existingProfile ?: try {
                getSecurityProfile(uid)
            } catch (_: Exception) {
                null
            }
            if (resolvedExisting == null || resolvedExisting.createdAt == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "saltBase64" to saltBase64,
                    "verifierHashBase64" to verifierHashBase64,
                    "iterations" to iterations.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitWriteOrQueueOffline(docRef.set(payload), OperationType.WRITE, docRef.path)
            } else {
                val updatePayload = mapOf(
                    "saltBase64" to saltBase64,
                    "verifierHashBase64" to verifierHashBase64,
                    "iterations" to iterations.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitWriteOrQueueOffline(docRef.update(updatePayload), OperationType.UPDATE, docRef.path)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- 2. Custom Journal Tags (/users/{userId}/tags/{tagId}) ---

    override fun observeTags(targetUserId: String): Flow<List<JournalTag>> = flow {
        val colRef = db.collection("users").document(targetUserId).collection("tags")
        emitAll(
            colRef.whereEqualTo("userId", targetUserId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    snap.toObjects(
                        JournalTag::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    override suspend fun createTag(name: String, colorHex: String): String {
        val uid = requireUserId()
        val tagId = "tag_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("tags").document(tagId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(40),
            "colorHex" to colorHex,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOrQueueOffline(docRef.set(payload), OperationType.CREATE, docRef.path)
            return tagId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun updateTag(tagId: String, name: String, colorHex: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tags").document(tagId)
        val payload = mapOf(
            "name" to name.trim().take(40),
            "colorHex" to colorHex,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOrQueueOffline(docRef.update(payload), OperationType.UPDATE, docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    override suspend fun deleteTag(tagId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tags").document(tagId)
        try {
            awaitWriteOrQueueOffline(docRef.delete(), OperationType.DELETE, docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 3. Encrypted Journal Entries (/users/{userId}/journals/{entryId}) ---

    override fun observeJournalEntries(targetUserId: String): Flow<List<JournalEntryDocument>> = flow {
        val colRef = db.collection("users").document(targetUserId).collection("journals")
        emitAll(
            colRef.whereEqualTo("userId", targetUserId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    snap.toObjects(
                        JournalEntryDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.entryDateEpochMs }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    override suspend fun getJournalEntryById(targetUserId: String, entryId: String): JournalEntryDocument? {
        val docRef = db.collection("users").document(targetUserId)
            .collection("journals").document(entryId)
        return try {
            val snap = getDocumentOfflineAware(docRef)
            snap?.toObject(
                JournalEntryDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
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
        val uid = requireUserId()
        val entryId = "jrn_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("journals").document(entryId)
        val payload = mapOf(
            "userId" to uid,
            "encryptedTitle" to encryptedTitle,
            "encryptedBody" to encryptedBody,
            "encryptedGratitudeResponse" to encryptedGratitudeResponse,
            "gratitudePromptText" to gratitudePromptText.take(500),
            "mood" to mood.name,
            "tagIds" to tagIds.distinct().take(20),
            "encryptedPhotoBase64" to encryptedPhotoBase64,
            "entryDateEpochMs" to entryDateEpochMs,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOrQueueOffline(docRef.set(payload), OperationType.CREATE, docRef.path)
            return entryId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
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
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("journals").document(entryId)
        val payload = mapOf(
            "encryptedTitle" to encryptedTitle,
            "encryptedBody" to encryptedBody,
            "encryptedGratitudeResponse" to encryptedGratitudeResponse,
            "gratitudePromptText" to gratitudePromptText.take(500),
            "mood" to mood.name,
            "tagIds" to tagIds.distinct().take(20),
            "encryptedPhotoBase64" to encryptedPhotoBase64,
            "entryDateEpochMs" to entryDateEpochMs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOrQueueOffline(docRef.update(payload), OperationType.UPDATE, docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    override suspend fun deleteJournalEntry(entryId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("journals").document(entryId)
        try {
            awaitWriteOrQueueOffline(docRef.delete(), OperationType.DELETE, docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 4. Daily Financial Expenses (/users/{userId}/expenses/{expenseId}) ---

    override fun observeExpenses(targetUserId: String): Flow<List<ExpenseItemDocument>> = flow {
        val colRef = db.collection("users").document(targetUserId).collection("expenses")
        emitAll(
            colRef.whereEqualTo("userId", targetUserId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    snap.toObjects(
                        ExpenseItemDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.spentAtEpochMs }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    override suspend fun createExpense(
        encryptedTitle: String,
        encryptedNotes: String,
        amountMinor: Long,
        currencyCode: String,
        category: ExpenseCategory,
        spentAtEpochMs: Long
    ): String {
        val uid = requireUserId()
        val expenseId = "exp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("expenses").document(expenseId)
        val payload = mapOf(
            "userId" to uid,
            "encryptedTitle" to encryptedTitle,
            "encryptedNotes" to encryptedNotes,
            "amountMinor" to amountMinor,
            "currencyCode" to currencyCode.uppercase().take(3),
            "category" to category.name,
            "spentAtEpochMs" to spentAtEpochMs,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOrQueueOffline(docRef.set(payload), OperationType.CREATE, docRef.path)
            return expenseId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun deleteExpense(expenseId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("expenses").document(expenseId)
        try {
            awaitWriteOrQueueOffline(docRef.delete(), OperationType.DELETE, docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 5. Multi-Timezone World Clocks (/users/{userId}/clocks/{clockId}) ---

    override fun observeClocks(targetUserId: String): Flow<List<WorldClockItem>> = flow {
        val colRef = db.collection("users").document(targetUserId).collection("clocks")
        emitAll(
            colRef.whereEqualTo("userId", targetUserId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    snap.toObjects(
                        WorldClockItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sortOrder }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    override suspend fun createClock(zoneId: String, cityLabel: String, sortOrder: Int): String {
        val uid = requireUserId()
        val clockId = "clk_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("clocks").document(clockId)
        val payload = mapOf(
            "userId" to uid,
            "zoneId" to zoneId.take(100),
            "cityLabel" to cityLabel.take(80),
            "sortOrder" to sortOrder.coerceIn(0, 1000).toLong(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOrQueueOffline(docRef.set(payload), OperationType.CREATE, docRef.path)
            return clockId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun deleteClock(clockId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("clocks").document(clockId)
        try {
            awaitWriteOrQueueOffline(docRef.delete(), OperationType.DELETE, docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
