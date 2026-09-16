package echo.music.iad1tya.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import echo.music.iad1tya.extensions.toEnum
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.get
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Do not synchronously read DataStore during composition. DataStore is asynchronous;
    // the old implementation used runBlocking through dataStore.get(), which could stall
    // the main thread during screen composition.
    val state = remember(key, defaultValue) {
        context.dataStore.data
            .map { prefs ->
                runCatching { prefs[key] }.getOrNull() ?: defaultValue
            }
            .distinctUntilChanged()
    }.collectAsState(initial = defaultValue)

    return remember(key, defaultValue, coroutineScope) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        runCatching {
                            context.dataStore.edit { it[key] = value }
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Start with the supplied default and let the Flow deliver the persisted value.
    val state = remember(key, defaultValue) {
        context.dataStore.data
            .map { prefs ->
                val raw = runCatching { prefs[key] }.getOrNull()
                raw.toEnum(defaultValue = defaultValue)
            }
            .distinctUntilChanged()
    }.collectAsState(initial = defaultValue)

    return remember(key, defaultValue, coroutineScope) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        runCatching {
                            context.dataStore.edit { it[key] = value.name }
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
