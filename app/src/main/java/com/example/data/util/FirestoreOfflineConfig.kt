package com.example.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.offlineSessionDataStore by preferencesDataStore(name = "vault_offline_session_prefs")

/**
 * Configures Firestore offline disk persistence (`PersistentCacheSettings`) on the named
 * database (`R.string.firestore_database_id`) so journal entries and expense records remain
 * available offline and automatically synchronize when network connectivity is restored.
 */
object FirestoreOfflineConfig {
    private val configuredDatabaseIds = mutableSetOf<String>()

    fun getConfiguredFirestore(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        ensurePersistentCacheEnabled(db, databaseId)
        return db
    }

    fun ensurePersistentCacheEnabled(db: FirebaseFirestore, databaseKey: String) {
        synchronized(configuredDatabaseIds) {
            if (!configuredDatabaseIds.add(databaseKey)) {
                return
            }
            try {
                val persistentCache = PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                val settings = FirebaseFirestoreSettings.Builder(db.firestoreSettings)
                    .setLocalCacheSettings(persistentCache)
                    .build()
                db.firestoreSettings = settings
            } catch (e: IllegalStateException) {
                // Firestore instance was already started in this process; existing cache settings remain active.
                Log.d("FirestoreOfflineConfig", "Firestore settings already started for $databaseKey: ${e.message}")
            } catch (e: Exception) {
                Log.w("FirestoreOfflineConfig", "Unable to customize PersistentCacheSettings for $databaseKey", e)
            }
        }
    }
}

/**
 * Observes live network availability using Android's ConnectivityManager so the repository
 * and UI know whether mutations will sync immediately or queue in Firestore's PersistentCache.
 */
class NetworkConnectivityObserver(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun isCurrentlyOnline(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isCurrentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.w("NetworkObserver", "registerNetworkCallback failed; using initial state", e)
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()
}

/**
 * Persists whether the user opted to use the app offline on-device without Google Sign-In.
 */
object OfflineSessionPreferences {
    const val LOCAL_OFFLINE_USER_ID = "local_offline_vault"
    private val KEY_OFFLINE_LOCAL_MODE = booleanPreferencesKey("is_offline_local_mode_enabled")

    fun observeOfflineLocalMode(context: Context): Flow<Boolean> {
        return context.applicationContext.offlineSessionDataStore.data.map { prefs ->
            prefs[KEY_OFFLINE_LOCAL_MODE] ?: false
        }
    }

    suspend fun setOfflineLocalMode(context: Context, enabled: Boolean) {
        context.applicationContext.offlineSessionDataStore.edit { prefs ->
            prefs[KEY_OFFLINE_LOCAL_MODE] = enabled
        }
    }
}
