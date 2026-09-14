package com.hexora.manager.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hexoraDataStore by preferencesDataStore(name = "hexora_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppSettingsRepository(private val context: Context) {
    private object Keys {
        val themeMode: Preferences.Key<String> = stringPreferencesKey("theme_mode")
    }

    val themeMode: Flow<ThemeMode> = context.hexoraDataStore.data.map { prefs ->
        prefs[Keys.themeMode]?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.hexoraDataStore.edit { it[Keys.themeMode] = mode.name }
    }
}
