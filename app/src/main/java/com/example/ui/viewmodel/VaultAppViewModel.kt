package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DecryptedExpenseItem
import com.example.data.model.DecryptedJournalEntry
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseItemDocument
import com.example.data.model.JournalEntryDocument
import com.example.data.model.JournalTag
import com.example.data.model.MoodType
import com.example.data.model.VaultSecurityProfile
import com.example.data.model.WorldClockItem
import com.example.data.network.CurrencyInfo
import com.example.data.network.CurrencyRepository
import com.example.data.network.ExchangeRatesSnapshot
import com.example.data.repository.VaultDataSource
import com.example.data.security.VaultCryptoManager
import com.example.data.util.GratitudePromptProvider
import com.example.data.util.PhotoAttachmentCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.crypto.SecretKey

sealed interface VaultLockState {
    data object Loading : VaultLockState
    data object NeedsPasscodeSetup : VaultLockState
    data class Locked(val errorMessage: String? = null) : VaultLockState
    data object Unlocked : VaultLockState
    data class Error(val message: String) : VaultLockState
}

enum class SyncModeStatus {
    LOCAL_ONLY_NO_ACCOUNT,
    CLOUD_OFFLINE_CACHED,
    CLOUD_PENDING_UPLOAD,
    CLOUD_SYNCED
}

data class CurrencyConverterUiState(
    val fromCurrency: String = "USD",
    val toCurrency: String = "EUR",
    val amountInput: String = "100.00",
    val ratesSnapshot: ExchangeRatesSnapshot? = null,
    val isRefreshing: Boolean = false
) {
    val convertedAmount: Double
        get() {
            val amount = amountInput.toDoubleOrNull() ?: 0.0
            val rate = ratesSnapshot?.rates?.get(toCurrency) ?: 1.0
            return amount * rate
        }

    val unitRate: Double
        get() = ratesSnapshot?.rates?.get(toCurrency) ?: 1.0
}

class VaultAppViewModel(
    private val repository: VaultDataSource,
    private val currencyRepository: CurrencyRepository,
    val currentUserId: String,
    isOnlineFlow: Flow<Boolean> = flowOf(true),
    private val onSyncPendingLocalVault: (suspend (SecretKey) -> Int)? = null
) : ViewModel() {

    val isCloudBacked: Boolean = repository.isCloudBacked

    private val _sessionAesKey = MutableStateFlow<SecretKey?>(null)
    val activeSessionKey: StateFlow<SecretKey?> = _sessionAesKey.asStateFlow()

    private val _lockError = MutableStateFlow<String?>(null)
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val syncStatus: StateFlow<SyncModeStatus> = combine(
        isOnlineFlow,
        repository.observeHasPendingSyncWrites(currentUserId).catch { emit(false) }
    ) { isOnline, hasPendingWrites ->
        when {
            !repository.isCloudBacked -> SyncModeStatus.LOCAL_ONLY_NO_ACCOUNT
            !isOnline -> SyncModeStatus.CLOUD_OFFLINE_CACHED
            hasPendingWrites -> SyncModeStatus.CLOUD_PENDING_UPLOAD
            else -> SyncModeStatus.CLOUD_SYNCED
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        if (repository.isCloudBacked) SyncModeStatus.CLOUD_SYNCED else SyncModeStatus.LOCAL_ONLY_NO_ACCOUNT
    )

    // Search & multi-tag filter state for Journal
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilterTagIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterTagIds: StateFlow<Set<String>> = _selectedFilterTagIds.asStateFlow()

    private val _selectedMoodFilter = MutableStateFlow<MoodType?>(null)
    val selectedMoodFilter: StateFlow<MoodType?> = _selectedMoodFilter.asStateFlow()

    // Expense currency display preference
    private val _displayCurrency = MutableStateFlow("USD")
    val displayCurrency: StateFlow<String> = _displayCurrency.asStateFlow()

    // Currency Converter state
    private val _converterState = MutableStateFlow(CurrencyConverterUiState())
    val converterState: StateFlow<CurrencyConverterUiState> = _converterState.asStateFlow()

    val supportedCurrencies: List<CurrencyInfo> = currencyRepository.supportedCurrencies

    val todayGratitudePrompt: String
        get() = GratitudePromptProvider.getPromptForDate(LocalDate.now())

    // 1. Security Profile observation
    private val securityProfileFlow: StateFlow<Result<VaultSecurityProfile?>> =
        repository.observeSecurityProfile(currentUserId)
            .map<VaultSecurityProfile?, Result<VaultSecurityProfile?>> { Result.success(it) }
            .catch { err ->
                Log.w("VaultVM", "Error observing security profile", err)
                emit(Result.failure(err))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), Result.success(null))

    private var hasLoadedInitialProfile = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                repository.getSecurityProfile(currentUserId)
            } catch (e: Exception) {
                Log.w("VaultVM", "Initial security profile check failed", e)
            } finally {
                hasLoadedInitialProfile.value = true
            }
        }
        refreshExchangeRates("USD")
    }

    val lockState: StateFlow<VaultLockState> = combine(
        hasLoadedInitialProfile,
        securityProfileFlow,
        _sessionAesKey,
        _lockError
    ) { loaded, profileRes, key, errMsg ->
        if (!loaded) {
            VaultLockState.Loading
        } else if (profileRes.isFailure) {
            VaultLockState.Error(profileRes.exceptionOrNull()?.localizedMessage ?: "Unable to load vault security profile")
        } else {
            val profile = profileRes.getOrNull()
            when {
                profile == null -> VaultLockState.NeedsPasscodeSetup
                key != null -> VaultLockState.Unlocked
                else -> VaultLockState.Locked(errMsg)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VaultLockState.Loading)

    // 2. Custom Tags Flow
    val tags: StateFlow<List<JournalTag>> = repository.observeTags(currentUserId)
        .catch { err ->
            Log.w("VaultVM", "Error observing tags", err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // 3. Raw Encrypted Journals -> Decrypted Journals when unlocked
    private val rawJournalsFlow = repository.observeJournalEntries(currentUserId)
        .catch { err ->
            Log.w("VaultVM", "Error observing journal entries", err)
            emit(emptyList())
        }

    val allDecryptedJournals: StateFlow<List<DecryptedJournalEntry>> = combine(
        rawJournalsFlow,
        _sessionAesKey
    ) { rawList, key ->
        if (key == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                rawList.map { doc -> decryptJournalDocument(doc, key) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredJournals: StateFlow<List<DecryptedJournalEntry>> = combine(
        allDecryptedJournals,
        _searchQuery,
        _selectedFilterTagIds,
        _selectedMoodFilter
    ) { entries, query, filterTagIds, moodFilter ->
        val trimmedQuery = query.trim().lowercase()
        entries.filter { entry ->
            val matchesTags = if (filterTagIds.isEmpty()) {
                true
            } else {
                // Multi-tag filter: entry must contain all selected filter tags
                entry.tagIds.containsAll(filterTagIds)
            }
            val matchesMood = moodFilter == null || entry.mood == moodFilter
            val matchesQuery = if (trimmedQuery.isEmpty()) {
                true
            } else {
                entry.title.lowercase().contains(trimmedQuery) ||
                    entry.body.lowercase().contains(trimmedQuery) ||
                    entry.gratitudeResponse.lowercase().contains(trimmedQuery) ||
                    entry.gratitudePromptText.lowercase().contains(trimmedQuery)
            }
            matchesTags && matchesMood && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // 4. Raw Encrypted Expenses -> Decrypted Expenses when unlocked
    private val rawExpensesFlow = repository.observeExpenses(currentUserId)
        .catch { err ->
            Log.w("VaultVM", "Error observing expenses", err)
            emit(emptyList())
        }

    val decryptedExpenses: StateFlow<List<DecryptedExpenseItem>> = combine(
        rawExpensesFlow,
        _sessionAesKey
    ) { rawList, key ->
        if (key == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                rawList.map { doc -> decryptExpenseDocument(doc, key) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // 5. World Clocks Flow
    val worldClocks: StateFlow<List<WorldClockItem>> = repository.observeClocks(currentUserId)
        .catch { err ->
            Log.w("VaultVM", "Error observing clocks", err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // --- Biometric & Numeric PIN Vault Actions (Credential Manager) ---

    fun setupVaultPasscode(
        passcode: String,
        confirmPasscode: String,
        activityContext: Context? = null,
        credentialManager: androidx.credentials.CredentialManager? = null,
        userEmail: String? = null,
        enrollBiometrics: Boolean = false
    ) {
        val clean = passcode.trim()
        if (clean.length < 4 || clean.length > 8 || !clean.all { it.isDigit() }) {
            _lockError.value = "Numeric PIN must be 4 to 8 digits."
            return
        }
        if (clean != confirmPasscode.trim()) {
            _lockError.value = "PINs do not match. Please try again."
            return
        }
        _lockError.value = null
        _isBusy.value = true
        viewModelScope.launch {
            try {
                val salt = VaultCryptoManager.generateSaltBase64()
                val key = withContext(Dispatchers.Default) {
                    VaultCryptoManager.deriveAesKey(clean, salt)
                }
                val verifier = VaultCryptoManager.computeVerifierHashBase64(key, salt)
                repository.saveSecurityProfile(
                    saltBase64 = salt,
                    verifierHashBase64 = verifier,
                    iterations = VaultCryptoManager.DEFAULT_ITERATIONS,
                    existingProfile = null
                )
                if (enrollBiometrics && activityContext != null && credentialManager != null) {
                    com.example.data.security.VaultCredentialAccessHelper.cacheHardwareWrappedVaultKey(
                        activityContext,
                        currentUserId,
                        key
                    )
                    val passkeyRes = com.example.data.security.VaultCredentialAccessHelper.enrollBiometricPasskey(
                        activityContext = activityContext,
                        credentialManager = credentialManager,
                        userId = currentUserId,
                        userEmail = userEmail,
                        vaultAesKey = key
                    )
                    if (passkeyRes.isFailure) {
                        com.example.data.security.VaultCredentialAccessHelper.registerPinWithCredentialManager(
                            activityContext = activityContext,
                            credentialManager = credentialManager,
                            userId = currentUserId,
                            userEmail = userEmail,
                            numericPin = clean
                        )
                    }
                }
                _sessionAesKey.value = key
                onVaultUnlockedHooks(key)
            } catch (e: Exception) {
                _lockError.value = e.localizedMessage ?: "Failed to initialize encrypted vault."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun unlockVault(
        passcode: String,
        activityContext: Context? = null,
        credentialManager: androidx.credentials.CredentialManager? = null,
        userEmail: String? = null,
        saveToCredentialManager: Boolean = false
    ) {
        val clean = passcode.trim()
        if (clean.isEmpty()) {
            _lockError.value = "Please enter your numeric PIN."
            return
        }
        _lockError.value = null
        _isBusy.value = true
        viewModelScope.launch {
            try {
                val profile = securityProfileFlow.value.getOrNull()
                    ?: repository.getSecurityProfile(currentUserId)
                if (profile == null) {
                    _lockError.value = "Vault profile not found."
                    return@launch
                }
                val derivedKey = withContext(Dispatchers.Default) {
                    VaultCryptoManager.deriveAesKey(clean, profile.saltBase64, profile.iterations.toInt())
                }
                val isValid = VaultCryptoManager.verifyKey(
                    derivedKey,
                    profile.saltBase64,
                    profile.verifierHashBase64
                )
                if (isValid) {
                    if (activityContext != null) {
                        com.example.data.security.VaultCredentialAccessHelper.cacheHardwareWrappedVaultKey(
                            activityContext,
                            currentUserId,
                            derivedKey
                        )
                    }
                    if (saveToCredentialManager && activityContext != null && credentialManager != null) {
                        val passkeyRes = com.example.data.security.VaultCredentialAccessHelper.enrollBiometricPasskey(
                            activityContext = activityContext,
                            credentialManager = credentialManager,
                            userId = currentUserId,
                            userEmail = userEmail,
                            vaultAesKey = derivedKey
                        )
                        if (passkeyRes.isFailure) {
                            com.example.data.security.VaultCredentialAccessHelper.registerPinWithCredentialManager(
                                activityContext = activityContext,
                                credentialManager = credentialManager,
                                userId = currentUserId,
                                userEmail = userEmail,
                                numericPin = clean
                            )
                        }
                    }
                    _sessionAesKey.value = derivedKey
                    _lockError.value = null
                    onVaultUnlockedHooks(derivedKey)
                } else {
                    _lockError.value = "Incorrect numeric PIN. Please try again."
                }
            } catch (e: Exception) {
                _lockError.value = e.localizedMessage ?: "Unlock failed."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun authenticateWithBiometricCredentialManager(
        activityContext: Context,
        credentialManager: androidx.credentials.CredentialManager
    ) {
        _lockError.value = null
        _isBusy.value = true
        viewModelScope.launch {
            try {
                val outcome = com.example.data.security.VaultCredentialAccessHelper.authenticateWithCredentialManager(
                    activityContext = activityContext,
                    credentialManager = credentialManager,
                    userId = currentUserId
                )
                when (outcome) {
                    is com.example.data.security.VaultCredentialAccessHelper.CredentialUnlockOutcome.UnlockedWithHardwareKey -> {
                        val profile = securityProfileFlow.value.getOrNull()
                            ?: repository.getSecurityProfile(currentUserId)
                        if (profile != null && VaultCryptoManager.verifyKey(
                                outcome.aesKey,
                                profile.saltBase64,
                                profile.verifierHashBase64
                            )
                        ) {
                            _sessionAesKey.value = outcome.aesKey
                            _lockError.value = null
                            onVaultUnlockedHooks(outcome.aesKey)
                        } else {
                            _lockError.value = "Saved biometric key out of sync. Please unlock once with your numeric PIN."
                        }
                    }
                    is com.example.data.security.VaultCredentialAccessHelper.CredentialUnlockOutcome.RetrievedNumericPin -> {
                        _isBusy.value = false
                        unlockVault(
                            passcode = outcome.numericPin,
                            activityContext = activityContext,
                            credentialManager = credentialManager
                        )
                        return@launch
                    }
                    com.example.data.security.VaultCredentialAccessHelper.CredentialUnlockOutcome.Cancelled -> {
                        // User dismissed sheet; allow seamless numeric PIN entry
                    }
                    is com.example.data.security.VaultCredentialAccessHelper.CredentialUnlockOutcome.Unavailable -> {
                        _lockError.value = outcome.reason
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun enrollBiometricQuickUnlockFromSettings(
        activityContext: Context,
        credentialManager: androidx.credentials.CredentialManager,
        userEmail: String?
    ) {
        val activeKey = _sessionAesKey.value ?: return
        viewModelScope.launch {
            val res = com.example.data.security.VaultCredentialAccessHelper.enrollBiometricPasskey(
                activityContext = activityContext,
                credentialManager = credentialManager,
                userId = currentUserId,
                userEmail = userEmail,
                vaultAesKey = activeKey
            )
            _statusBanner.value = if (res.isSuccess) {
                "Biometric Passkey enrolled in Credential Manager"
            } else {
                "Hardware key cached; use 'Save Biometric Unlock' on next PIN entry if Passkeys aren't configured on device"
            }
        }
    }

    fun lockVaultNow() {
        _sessionAesKey.value = null
        _lockError.value = null
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun postStatusMessage(message: String) {
        _statusBanner.value = message
    }

    private suspend fun onVaultUnlockedHooks(activeKey: SecretKey) {
        val migratedCount = try {
            onSyncPendingLocalVault?.invoke(activeKey) ?: 0
        } catch (e: Exception) {
            Log.w("VaultVM", "Local-to-cloud sync check skipped", e)
            0
        }
        if (migratedCount > 0) {
            _statusBanner.value = "Synced $migratedCount offline item(s) to your encrypted cloud vault"
        }
        seedDefaultStarterTagsAndClocksIfEmpty()
    }

    private suspend fun seedDefaultStarterTagsAndClocksIfEmpty() {
        try {
            if (tags.value.isEmpty()) {
                val starterTags = listOf(
                    "Work" to "#2D6A4F",
                    "Family" to "#E07A5F",
                    "Travel" to "#E6B34B",
                    "Ideas" to "#457B9D",
                    "Health" to "#81B29A"
                )
                for ((name, hex) in starterTags) {
                    repository.createTag(name, hex)
                }
            }
            if (worldClocks.value.isEmpty()) {
                val starterClocks = listOf(
                    Triple("America/New_York", "New York", 0),
                    Triple("Europe/London", "London", 1),
                    Triple("Asia/Tokyo", "Tokyo", 2),
                    Triple("Asia/Singapore", "Singapore", 3)
                )
                for ((zone, city, idx) in starterClocks) {
                    repository.createClock(zone, city, idx)
                }
            }
        } catch (e: Exception) {
            Log.w("VaultVM", "Starter tags/clocks seeding skipped", e)
        }
    }

    // --- Journal & Gratitude Actions ---

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFilterTag(tagId: String) {
        val current = _selectedFilterTagIds.value
        _selectedFilterTagIds.value = if (current.contains(tagId)) {
            current - tagId
        } else {
            current + tagId
        }
    }

    fun clearTagFilters() {
        _selectedFilterTagIds.value = emptySet()
        _selectedMoodFilter.value = null
    }

    fun setMoodFilter(mood: MoodType?) {
        _selectedMoodFilter.value = if (_selectedMoodFilter.value == mood) null else mood
    }

    fun saveJournalEntry(
        context: Context,
        editingEntryId: String?,
        title: String,
        body: String,
        gratitudeResponse: String,
        gratitudePromptText: String,
        mood: MoodType,
        tagIds: List<String>,
        pickedPhotoUri: Uri?,
        existingEncryptedPhotoBase64: String,
        removePhoto: Boolean,
        entryDateEpochMs: Long,
        onComplete: () -> Unit
    ) {
        val key = _sessionAesKey.value ?: return
        val safeTitle = title.trim().ifBlank { "Untitled Reflection" }
        val safeBody = body.trim().ifBlank { gratitudeResponse.trim().ifBlank { "Moment captured." } }

        _isBusy.value = true
        viewModelScope.launch {
            try {
                val encryptedPhoto = when {
                    removePhoto -> ""
                    pickedPhotoUri != null -> {
                        val compressed = PhotoAttachmentCompressor.compressUriToJpegBytes(context, pickedPhotoUri)
                        if (compressed != null) {
                            withContext(Dispatchers.Default) {
                                VaultCryptoManager.encryptBytesToBase64(compressed, key)
                            }
                        } else {
                            existingEncryptedPhotoBase64
                        }
                    }
                    else -> existingEncryptedPhotoBase64
                }

                val encTitle = withContext(Dispatchers.Default) {
                    VaultCryptoManager.encryptText(safeTitle, key)
                }
                val encBody = withContext(Dispatchers.Default) {
                    VaultCryptoManager.encryptText(safeBody, key)
                }
                val encGratitude = withContext(Dispatchers.Default) {
                    if (gratitudeResponse.isBlank()) "" else VaultCryptoManager.encryptText(gratitudeResponse.trim(), key)
                }

                if (editingEntryId.isNullOrBlank()) {
                    repository.createJournalEntry(
                        encryptedTitle = encTitle,
                        encryptedBody = encBody,
                        encryptedGratitudeResponse = encGratitude,
                        gratitudePromptText = gratitudePromptText,
                        mood = mood,
                        tagIds = tagIds,
                        encryptedPhotoBase64 = encryptedPhoto,
                        entryDateEpochMs = entryDateEpochMs
                    )
                    _statusBanner.value = when (syncStatus.value) {
                        SyncModeStatus.LOCAL_ONLY_NO_ACCOUNT -> "Encrypted entry saved to offline vault"
                        SyncModeStatus.CLOUD_OFFLINE_CACHED,
                        SyncModeStatus.CLOUD_PENDING_UPLOAD -> "Saved offline • Will auto-sync when online"
                        SyncModeStatus.CLOUD_SYNCED -> "Encrypted entry synced to cloud"
                    }
                } else {
                    repository.updateJournalEntry(
                        entryId = editingEntryId,
                        encryptedTitle = encTitle,
                        encryptedBody = encBody,
                        encryptedGratitudeResponse = encGratitude,
                        gratitudePromptText = gratitudePromptText,
                        mood = mood,
                        tagIds = tagIds,
                        encryptedPhotoBase64 = encryptedPhoto,
                        entryDateEpochMs = entryDateEpochMs
                    )
                    _statusBanner.value = when (syncStatus.value) {
                        SyncModeStatus.LOCAL_ONLY_NO_ACCOUNT -> "Journal entry updated in offline vault"
                        SyncModeStatus.CLOUD_OFFLINE_CACHED,
                        SyncModeStatus.CLOUD_PENDING_UPLOAD -> "Updated offline • Will auto-sync when online"
                        SyncModeStatus.CLOUD_SYNCED -> "Journal entry updated"
                    }
                }
                onComplete()
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to save encrypted entry"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteJournalEntry(entryId: String) {
        viewModelScope.launch {
            try {
                repository.deleteJournalEntry(entryId)
                _statusBanner.value = "Journal entry deleted"
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to delete entry"
            }
        }
    }

    // --- Custom Tag CRUD ---

    fun createTag(name: String, colorHex: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.createTag(clean, colorHex)
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Could not create tag"
            }
        }
    }

    fun updateTag(tagId: String, name: String, colorHex: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.updateTag(tagId, clean, colorHex)
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Could not update tag"
            }
        }
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            try {
                repository.deleteTag(tagId)
                _selectedFilterTagIds.value = _selectedFilterTagIds.value - tagId
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Could not delete tag"
            }
        }
    }

    // --- Financial Expense Tracker Actions ---

    fun addExpense(
        title: String,
        notes: String,
        amountMajor: Double,
        currencyCode: String,
        category: ExpenseCategory,
        spentAtEpochMs: Long,
        onComplete: () -> Unit
    ) {
        val key = _sessionAesKey.value ?: return
        val amountMinor = (amountMajor * 100.0).toLong()
        if (amountMinor <= 0L) {
            _statusBanner.value = "Enter an expense amount greater than zero"
            return
        }
        val cleanTitle = title.trim().ifBlank { category.label }
        _isBusy.value = true
        viewModelScope.launch {
            try {
                val encTitle = withContext(Dispatchers.Default) {
                    VaultCryptoManager.encryptText(cleanTitle, key)
                }
                val encNotes = withContext(Dispatchers.Default) {
                    if (notes.isBlank()) "" else VaultCryptoManager.encryptText(notes.trim(), key)
                }
                repository.createExpense(
                    encryptedTitle = encTitle,
                    encryptedNotes = encNotes,
                    amountMinor = amountMinor,
                    currencyCode = currencyCode,
                    category = category,
                    spentAtEpochMs = spentAtEpochMs
                )
                _statusBanner.value = when (syncStatus.value) {
                    SyncModeStatus.LOCAL_ONLY_NO_ACCOUNT -> "Expense saved to offline vault"
                    SyncModeStatus.CLOUD_OFFLINE_CACHED,
                    SyncModeStatus.CLOUD_PENDING_UPLOAD -> "Expense saved offline • Queued for sync"
                    SyncModeStatus.CLOUD_SYNCED -> "Expense logged & synced"
                }
                onComplete()
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to save expense"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            try {
                repository.deleteExpense(expenseId)
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to delete expense"
            }
        }
    }

    fun setDisplayCurrency(code: String) {
        _displayCurrency.value = code
        refreshExchangeRates(code)
    }

    // --- Currency Converter & Multi-Timezone Clocks ---

    fun updateConverterAmount(input: String) {
        _converterState.value = _converterState.value.copy(amountInput = input)
    }

    fun updateConverterFromCurrency(code: String) {
        _converterState.value = _converterState.value.copy(fromCurrency = code)
        refreshExchangeRates(code)
    }

    fun updateConverterToCurrency(code: String) {
        _converterState.value = _converterState.value.copy(toCurrency = code)
    }

    fun swapConverterCurrencies() {
        val cur = _converterState.value
        _converterState.value = cur.copy(
            fromCurrency = cur.toCurrency,
            toCurrency = cur.fromCurrency
        )
        refreshExchangeRates(cur.toCurrency)
    }

    fun refreshExchangeRates(baseCurrency: String = _converterState.value.fromCurrency) {
        _converterState.value = _converterState.value.copy(isRefreshing = true)
        viewModelScope.launch {
            val snap = currencyRepository.fetchRates(baseCurrency)
            _converterState.value = _converterState.value.copy(
                fromCurrency = baseCurrency,
                ratesSnapshot = snap,
                isRefreshing = false
            )
        }
    }

    fun addWorldClock(zoneId: String, cityLabel: String) {
        viewModelScope.launch {
            try {
                val nextIndex = worldClocks.value.size
                repository.createClock(zoneId, cityLabel, nextIndex)
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to add clock"
            }
        }
    }

    fun deleteWorldClock(clockId: String) {
        viewModelScope.launch {
            try {
                repository.deleteClock(clockId)
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to remove clock"
            }
        }
    }

    private fun decryptJournalDocument(doc: JournalEntryDocument, key: SecretKey): DecryptedJournalEntry {
        val title = VaultCryptoManager.decryptText(doc.encryptedTitle, key).ifBlank { "Encrypted Entry" }
        val body = VaultCryptoManager.decryptText(doc.encryptedBody, key)
        val gratitude = VaultCryptoManager.decryptText(doc.encryptedGratitudeResponse, key)
        val photoBytes = VaultCryptoManager.decryptBytesFromBase64(doc.encryptedPhotoBase64, key)
        return DecryptedJournalEntry(
            id = doc.id,
            title = title,
            body = body,
            gratitudeResponse = gratitude,
            gratitudePromptText = doc.gratitudePromptText,
            mood = MoodType.fromKey(doc.mood),
            tagIds = doc.tagIds,
            photoBytes = photoBytes,
            encryptedPhotoBase64 = doc.encryptedPhotoBase64,
            entryDateEpochMs = doc.entryDateEpochMs,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt
        )
    }

    private fun decryptExpenseDocument(doc: ExpenseItemDocument, key: SecretKey): DecryptedExpenseItem {
        val title = VaultCryptoManager.decryptText(doc.encryptedTitle, key).ifBlank { "Expense" }
        val notes = VaultCryptoManager.decryptText(doc.encryptedNotes, key)
        return DecryptedExpenseItem(
            id = doc.id,
            title = title,
            notes = notes,
            amountMinor = doc.amountMinor,
            currencyCode = doc.currencyCode,
            category = ExpenseCategory.fromKey(doc.category),
            spentAtEpochMs = doc.spentAtEpochMs,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt
        )
    }
}
