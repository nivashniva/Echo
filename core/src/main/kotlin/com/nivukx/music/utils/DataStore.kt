package com.nivukx.music.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.nivukx.music.extensions.toEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Hot in-memory DataStore snapshot used by legacy synchronous call sites.
 *
 * The previous implementation used runBlocking for every read, which could block
 * the main thread on DataStore I/O. The snapshot is refreshed continuously and
 * synchronous reads become O(1). Cold callers can use the suspend variants below
 * when they need a guaranteed disk-backed value.
 */
object DataStoreSnapshot {
    private val snapshot = AtomicReference<Preferences?>(null)
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(dataStore: DataStore<Preferences>) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            dataStore.data.collect { preferences ->
                snapshot.set(preferences)
            }
        }
    }

    fun <T> get(key: Preferences.Key<T>): T? = snapshot.get()?.get(key)

    fun <T> get(key: Preferences.Key<T>, defaultValue: T): T = snapshot.get()?.get(key) ?: defaultValue

    suspend fun <T> getAsync(dataStore: DataStore<Preferences>, key: Preferences.Key<T>): T? =
        withContext(Dispatchers.IO) {
            runCatching { dataStore.data.first()[key] }.getOrNull()
        }

    suspend fun <T> getAsync(
        dataStore: DataStore<Preferences>,
        key: Preferences.Key<T>,
        defaultValue: T,
    ): T = getAsync(dataStore, key) ?: defaultValue
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    DataStoreSnapshot.get(key)

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = DataStoreSnapshot.get(key) ?: defaultValue

suspend fun <T> DataStore<Preferences>.getAsync(key: Preferences.Key<T>): T? =
    DataStoreSnapshot.get(key) ?: DataStoreSnapshot.getAsync(this, key)

suspend fun <T> DataStore<Preferences>.getAsync(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    DataStoreSnapshot.get<T>(key) ?: DataStoreSnapshot.getAsync(this, key, defaultValue)

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore.get(key, defaultValue) }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore.get(key, defaultValue).toEnum(defaultValue) }
