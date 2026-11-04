package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalVaultDatabase
import com.example.data.model.DecryptedJournalEntry
import com.example.data.network.CurrencyRepository
import com.example.data.repository.LocalRoomVaultRepository
import com.example.data.repository.LocalVaultSyncBridge
import com.example.data.repository.VaultRepository
import com.example.data.security.BiometricEnrollmentStatus
import com.example.data.security.VaultCredentialAccessHelper
import com.example.data.util.FirestoreOfflineConfig
import com.example.data.util.NetworkConnectivityObserver
import com.example.data.util.OfflineSessionPreferences
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.GoogleCredentialAuthHelper
import com.example.ui.screens.ExpensesTabScreen
import com.example.ui.screens.GlobeAndCurrencyTabScreen
import com.example.ui.screens.JournalEditorScreen
import com.example.ui.screens.JournalTabScreen
import com.example.ui.screens.PasscodeVaultScreen
import com.example.ui.screens.TagManagementSheet
import com.example.ui.screens.VaultSettingsTabScreen
import com.example.ui.theme.VaultJournalTheme
import com.example.ui.viewmodel.VaultAppViewModel
import com.example.ui.viewmodel.VaultLockState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultJournalTheme {
                AppAuthGateRoot()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppAuthGateRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val isOfflineLocalMode by remember(context) {
        OfflineSessionPreferences.observeOfflineLocalMode(context)
    }.collectAsStateWithLifecycle(initialValue = false)

    var carriedLocalAesKey by remember { mutableStateOf<SecretKey?>(null) }

    val user = currentUser
    when {
        user != null -> {
            AuthenticatedVaultHost(
                currentUserId = user.uid,
                userEmail = user.email,
                carriedLocalAesKey = carriedLocalAesKey,
                onConsumeCarriedKey = { carriedLocalAesKey = null }
            )
        }

        isOfflineLocalMode -> {
            OfflineLocalVaultHost(
                onExitOfflineMode = {
                    scope.launch {
                        OfflineSessionPreferences.setOfflineLocalMode(context, false)
                    }
                },
                onCarryKeyAndConnectGoogle = { activeLocalKey ->
                    carriedLocalAesKey = activeLocalKey
                    scope.launch {
                        OfflineSessionPreferences.setOfflineLocalMode(context, false)
                    }
                }
            )
        }

        else -> {
            AuthScreen(
                onAuthSuccess = {},
                onContinueOffline = {
                    scope.launch {
                        OfflineSessionPreferences.setOfflineLocalMode(context, true)
                    }
                }
            )
        }
    }
}

enum class MainDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    JOURNAL(R.string.nav_journal, Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "nav_tab_journal"),
    EXPENSES(R.string.nav_expenses, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_tab_expenses"),
    GLOBE(R.string.nav_globe, Icons.Filled.Public, Icons.Outlined.Public, "nav_tab_globe"),
    VAULT(R.string.nav_vault, Icons.Filled.Lock, Icons.Outlined.Lock, "nav_tab_vault")
}

@Composable
fun AuthenticatedVaultHost(
    currentUserId: String,
    userEmail: String?,
    carriedLocalAesKey: SecretKey? = null,
    onConsumeCarriedKey: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isPreSyncChecking by remember(currentUserId) { mutableStateOf(true) }

    // If the user created entries in Offline Local Mode and just signed into a fresh Google
    // account that has no cloud security profile yet, adopt their local profile & upload
    // encrypted records before instantiating the ViewModel so they seamlessly unlock!
    LaunchedEffect(currentUserId) {
        try {
            val localDao = LocalVaultDatabase.getInstance(context).vaultDao()
            if (LocalVaultSyncBridge.hasPendingLocalData(localDao)) {
                val db = FirestoreOfflineConfig.getConfiguredFirestore(context)
                val netObserver = NetworkConnectivityObserver(context)
                val cloudRepo = VaultRepository(
                    db = db,
                    uidProvider = { currentUserId },
                    isOnlineProvider = netObserver::isCurrentlyOnline
                )
                val existingCloudProfile = cloudRepo.getSecurityProfile(currentUserId)
                if (existingCloudProfile == null) {
                    LocalVaultSyncBridge.migrateLocalVaultToCloudIfEligible(
                        dao = localDao,
                        cloudRepo = cloudRepo,
                        authenticatedUid = currentUserId,
                        localAesKey = carriedLocalAesKey,
                        cloudAesKey = carriedLocalAesKey
                    )
                    onConsumeCarriedKey()
                }
            }
        } catch (_: Exception) {
        } finally {
            isPreSyncChecking = false
        }
    }

    if (isPreSyncChecking) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val viewModel: VaultAppViewModel = viewModel(
        key = "cloud_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = FirestoreOfflineConfig.getConfiguredFirestore(app)
                val networkObserver = NetworkConnectivityObserver(app)
                val cloudRepo = VaultRepository(
                    db = db,
                    uidProvider = { Firebase.auth.currentUser?.uid },
                    isOnlineProvider = networkObserver::isCurrentlyOnline
                )
                val localDao = LocalVaultDatabase.getInstance(app).vaultDao()
                VaultAppViewModel(
                    repository = cloudRepo,
                    currencyRepository = CurrencyRepository(),
                    currentUserId = currentUserId,
                    isOnlineFlow = networkObserver.isOnlineFlow,
                    onSyncPendingLocalVault = { unlockedCloudKey ->
                        val count = LocalVaultSyncBridge.migrateLocalVaultToCloudIfEligible(
                            dao = localDao,
                            cloudRepo = cloudRepo,
                            authenticatedUid = currentUserId,
                            localAesKey = carriedLocalAesKey,
                            cloudAesKey = unlockedCloudKey
                        )
                        if (count > 0) {
                            onConsumeCarriedKey()
                        }
                        count
                    }
                )
            }
        }
    )

    VaultSessionScaffold(
        viewModel = viewModel,
        currentUserId = currentUserId,
        userEmail = userEmail,
        isLocalOfflineMode = false,
        credentialManager = credentialManager,
        onSignOut = {
            viewModel.lockVaultNow()
            scope.launch {
                OfflineSessionPreferences.setOfflineLocalMode(context, false)
            }
            GoogleCredentialAuthHelper.signOut(credentialManager, {}, scope)
        },
        onConnectGoogleForSync = null
    )
}

/**
 * Pure on-device vault host when running offline without Google Sign-In.
 * Never instantiates `FirebaseFirestore` or executes unauthenticated Firestore queries.
 */
@Composable
fun OfflineLocalVaultHost(
    onExitOfflineMode: () -> Unit,
    onCarryKeyAndConnectGoogle: (SecretKey?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val localUserId = OfflineSessionPreferences.LOCAL_OFFLINE_USER_ID
    var isConnectingGoogle by remember { mutableStateOf(false) }

    val viewModel: VaultAppViewModel = viewModel(
        key = "offline_$localUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localDao = LocalVaultDatabase.getInstance(app).vaultDao()
                VaultAppViewModel(
                    repository = LocalRoomVaultRepository(localDao),
                    currencyRepository = CurrencyRepository(),
                    currentUserId = localUserId,
                    isOnlineFlow = flowOf(false)
                )
            }
        }
    )

    VaultSessionScaffold(
        viewModel = viewModel,
        currentUserId = localUserId,
        userEmail = null,
        isLocalOfflineMode = true,
        credentialManager = credentialManager,
        isConnectingGoogle = isConnectingGoogle,
        onSignOut = {
            viewModel.lockVaultNow()
            onExitOfflineMode()
        },
        onConnectGoogleForSync = {
            isConnectingGoogle = true
            val currentLocalKey = viewModel.activeSessionKey.value
            GoogleCredentialAuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isConnectingGoogle = false
                    onCarryKeyAndConnectGoogle(currentLocalKey)
                },
                onAuthError = { msg ->
                    isConnectingGoogle = false
                    viewModel.postStatusMessage(msg)
                },
                scope = scope,
                onAuthCancelled = {
                    isConnectingGoogle = false
                }
            )
        }
    )
}

@Composable
private fun VaultSessionScaffold(
    viewModel: VaultAppViewModel,
    currentUserId: String,
    userEmail: String?,
    isLocalOfflineMode: Boolean,
    credentialManager: CredentialManager,
    onSignOut: () -> Unit,
    isConnectingGoogle: Boolean = false,
    onConnectGoogleForSync: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val biometricStatus by remember(context, currentUserId) {
        VaultCredentialAccessHelper.observeBiometricStatus(context, currentUserId)
    }.collectAsStateWithLifecycle(initialValue = BiometricEnrollmentStatus())

    when (val state = lockState) {
        VaultLockState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        VaultLockState.NeedsPasscodeSetup -> {
            PasscodeVaultScreen(
                isSetupMode = true,
                userEmail = userEmail,
                errorMessage = null,
                isBusy = isBusy,
                biometricStatus = biometricStatus,
                isLocalOfflineMode = isLocalOfflineMode,
                onSetupPasscode = { pin, confirmPin, enrollBio ->
                    viewModel.setupVaultPasscode(
                        passcode = pin,
                        confirmPasscode = confirmPin,
                        activityContext = context,
                        credentialManager = credentialManager,
                        userEmail = userEmail,
                        enrollBiometrics = enrollBio
                    )
                },
                onUnlockPasscode = { _, _ -> },
                onBiometricUnlockClick = {},
                onSignOut = onSignOut
            )
        }

        is VaultLockState.Locked -> {
            PasscodeVaultScreen(
                isSetupMode = false,
                userEmail = userEmail,
                errorMessage = state.errorMessage,
                isBusy = isBusy,
                biometricStatus = biometricStatus,
                isLocalOfflineMode = isLocalOfflineMode,
                onSetupPasscode = { _, _, _ -> },
                onUnlockPasscode = { pin, saveCred ->
                    viewModel.unlockVault(
                        passcode = pin,
                        activityContext = context,
                        credentialManager = credentialManager,
                        userEmail = userEmail,
                        saveToCredentialManager = saveCred
                    )
                },
                onBiometricUnlockClick = {
                    viewModel.authenticateWithBiometricCredentialManager(
                        activityContext = context,
                        credentialManager = credentialManager
                    )
                },
                onSignOut = onSignOut
            )
        }

        is VaultLockState.Error -> {
            PasscodeVaultScreen(
                isSetupMode = false,
                userEmail = userEmail,
                errorMessage = state.message,
                isBusy = isBusy,
                biometricStatus = biometricStatus,
                isLocalOfflineMode = isLocalOfflineMode,
                onSetupPasscode = { pin, confirmPin, enrollBio ->
                    viewModel.setupVaultPasscode(
                        passcode = pin,
                        confirmPasscode = confirmPin,
                        activityContext = context,
                        credentialManager = credentialManager,
                        userEmail = userEmail,
                        enrollBiometrics = enrollBio
                    )
                },
                onUnlockPasscode = { pin, saveCred ->
                    viewModel.unlockVault(
                        passcode = pin,
                        activityContext = context,
                        credentialManager = credentialManager,
                        userEmail = userEmail,
                        saveToCredentialManager = saveCred
                    )
                },
                onBiometricUnlockClick = {
                    viewModel.authenticateWithBiometricCredentialManager(
                        activityContext = context,
                        credentialManager = credentialManager
                    )
                },
                onSignOut = onSignOut
            )
        }

        VaultLockState.Unlocked -> {
            UnlockedSanctuaryShell(
                viewModel = viewModel,
                userEmail = userEmail,
                biometricStatus = biometricStatus,
                credentialManager = credentialManager,
                isConnectingGoogle = isConnectingGoogle,
                onConnectGoogleForSync = onConnectGoogleForSync,
                onSignOut = onSignOut
            )
        }
    }
}

@Composable
private fun UnlockedSanctuaryShell(
    viewModel: VaultAppViewModel,
    userEmail: String?,
    biometricStatus: BiometricEnrollmentStatus,
    credentialManager: CredentialManager,
    onSignOut: () -> Unit,
    isConnectingGoogle: Boolean = false,
    onConnectGoogleForSync: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val allEntries by viewModel.allDecryptedJournals.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredJournals.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilterTagIds by viewModel.selectedFilterTagIds.collectAsStateWithLifecycle()
    val selectedMoodFilter by viewModel.selectedMoodFilter.collectAsStateWithLifecycle()
    val expenses by viewModel.decryptedExpenses.collectAsStateWithLifecycle()
    val displayCurrency by viewModel.displayCurrency.collectAsStateWithLifecycle()
    val converterState by viewModel.converterState.collectAsStateWithLifecycle()
    val worldClocks by viewModel.worldClocks.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainDestination.JOURNAL) }
    var isEditorOpen by rememberSaveable { mutableStateOf(false) }
    var editingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTagManagerSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    if (currentTab != MainDestination.JOURNAL && !isEditorOpen) {
        BackHandler {
            currentTab = MainDestination.JOURNAL
        }
    }

    val editingEntry: DecryptedJournalEntry? = remember(editingEntryId, allEntries) {
        editingEntryId?.let { id -> allEntries.firstOrNull { it.id == id } }
    }

    if (isEditorOpen) {
        JournalEditorScreen(
            existingEntry = editingEntry,
            tags = tags,
            isBusy = isBusy,
            onManageTagsClick = { showTagManagerSheet = true },
            onSave = { title, body, gratitudeResponse, gratitudePromptText, mood, tagIds, pickedUri, existingPhotoB64, removePhoto, entryDateMs ->
                viewModel.saveJournalEntry(
                    context = context,
                    editingEntryId = editingEntryId,
                    title = title,
                    body = body,
                    gratitudeResponse = gratitudeResponse,
                    gratitudePromptText = gratitudePromptText,
                    mood = mood,
                    tagIds = tagIds,
                    pickedPhotoUri = pickedUri,
                    existingEncryptedPhotoBase64 = existingPhotoB64,
                    removePhoto = removePhoto,
                    entryDateEpochMs = entryDateMs,
                    onComplete = {
                        isEditorOpen = false
                        editingEntryId = null
                    }
                )
            },
            onBack = {
                isEditorOpen = false
                editingEntryId = null
            }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            MainDestination.entries.forEach { dest ->
                                val selected = currentTab == dest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(id = dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(id = dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail {
                            MainDestination.entries.forEach { dest ->
                                val selected = currentTab == dest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(id = dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(id = dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            MainDestination.JOURNAL -> JournalTabScreen(
                                allEntries = allEntries,
                                filteredEntries = filteredEntries,
                                tags = tags,
                                todayGratitudePrompt = viewModel.todayGratitudePrompt,
                                searchQuery = searchQuery,
                                selectedFilterTagIds = selectedFilterTagIds,
                                selectedMoodFilter = selectedMoodFilter,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onToggleTagFilter = viewModel::toggleFilterTag,
                                onSelectMoodFilter = viewModel::setMoodFilter,
                                onClearFilters = viewModel::clearTagFilters,
                                onOpenTagManager = { showTagManagerSheet = true },
                                onNewEntryClick = {
                                    editingEntryId = null
                                    isEditorOpen = true
                                },
                                onEditEntryClick = { entry ->
                                    editingEntryId = entry.id
                                    isEditorOpen = true
                                },
                                onDeleteEntryClick = viewModel::deleteJournalEntry,
                                onQuickLockClick = viewModel::lockVaultNow,
                                syncStatus = syncStatus
                            )

                            MainDestination.EXPENSES -> ExpensesTabScreen(
                                expenses = expenses,
                                displayCurrency = displayCurrency,
                                supportedCurrencies = viewModel.supportedCurrencies,
                                ratesSnapshot = converterState.ratesSnapshot,
                                onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                                onAddExpense = { title, notes, amountMajor, currencyCode, category ->
                                    viewModel.addExpense(
                                        title = title,
                                        notes = notes,
                                        amountMajor = amountMajor,
                                        currencyCode = currencyCode,
                                        category = category,
                                        spentAtEpochMs = System.currentTimeMillis(),
                                        onComplete = {}
                                    )
                                },
                                onDeleteExpense = viewModel::deleteExpense,
                                syncStatus = syncStatus
                            )

                            MainDestination.GLOBE -> GlobeAndCurrencyTabScreen(
                                converterState = converterState,
                                supportedCurrencies = viewModel.supportedCurrencies,
                                worldClocks = worldClocks,
                                onAmountChange = viewModel::updateConverterAmount,
                                onFromCurrencyChange = viewModel::updateConverterFromCurrency,
                                onToCurrencyChange = viewModel::updateConverterToCurrency,
                                onSwapCurrencies = viewModel::swapConverterCurrencies,
                                onRefreshRates = { viewModel.refreshExchangeRates() },
                                onAddWorldClock = viewModel::addWorldClock,
                                onDeleteWorldClock = viewModel::deleteWorldClock
                            )

                            MainDestination.VAULT -> VaultSettingsTabScreen(
                                userEmail = userEmail,
                                journalCount = allEntries.size,
                                tagCount = tags.size,
                                expenseCount = expenses.size,
                                clockCount = worldClocks.size,
                                isBiometricEnrolled = biometricStatus.isBiometricQuickUnlockReady,
                                onEnrollBiometricClick = {
                                    viewModel.enrollBiometricQuickUnlockFromSettings(
                                        activityContext = context,
                                        credentialManager = credentialManager,
                                        userEmail = userEmail
                                    )
                                },
                                onLockVaultNow = viewModel::lockVaultNow,
                                onOpenTagManager = { showTagManagerSheet = true },
                                onSignOut = onSignOut,
                                syncStatus = syncStatus,
                                isConnectingGoogle = isConnectingGoogle,
                                onConnectGoogleForSyncClick = onConnectGoogleForSync
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTagManagerSheet) {
        TagManagementSheet(
            tags = tags,
            onCreateTag = viewModel::createTag,
            onUpdateTag = viewModel::updateTag,
            onDeleteTag = viewModel::deleteTag,
            onDismiss = { showTagManagerSheet = false }
        )
    }
}
