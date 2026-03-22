package com.dvait.base.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        val BLACKLISTED_APPS = stringSetPreferencesKey("blacklisted_apps")
        val WHITELISTED_APPS = stringSetPreferencesKey("whitelisted_apps")
        val CAPTURE_NOTIFICATIONS = booleanPreferencesKey("capture_notifications")
        val USE_WHITELIST_MODE = booleanPreferencesKey("use_whitelist_mode")
        val APP_THEME = stringPreferencesKey("app_theme")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val GROQ_MODEL = stringPreferencesKey("groq_model")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }


    val blacklistedApps: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[BLACKLISTED_APPS] ?: emptySet()
    }

    val whitelistedApps: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[WHITELISTED_APPS] ?: emptySet()
    }

    val captureNotifications: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[CAPTURE_NOTIFICATIONS] ?: false
    }

    val useWhitelistMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[USE_WHITELIST_MODE] ?: true
    }


    val appTheme: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[APP_THEME] ?: "system"
    }

    val groqApiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[GROQ_API_KEY] ?: ""
    }

    val groqModel: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[GROQ_MODEL] ?: "llama-3.3-70b-versatile"
    }

    val accentColor: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[ACCENT_COLOR] ?: "orange"
    }

    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }


    suspend fun setBlacklistedApps(apps: Set<String>) {
        context.settingsDataStore.edit { it[BLACKLISTED_APPS] = apps }
    }

    suspend fun setWhitelistedApps(apps: Set<String>) {
        context.settingsDataStore.edit { it[WHITELISTED_APPS] = apps }
    }

    suspend fun setCaptureNotifications(enabled: Boolean) {
        context.settingsDataStore.edit { it[CAPTURE_NOTIFICATIONS] = enabled }
    }

    suspend fun setUseWhitelistMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[USE_WHITELIST_MODE] = enabled }
    }


    suspend fun setAppTheme(theme: String) {
        context.settingsDataStore.edit { it[APP_THEME] = theme }
    }

    suspend fun setGroqApiKey(key: String) {
        context.settingsDataStore.edit { it[GROQ_API_KEY] = key }
    }

    suspend fun setGroqModel(model: String) {
        context.settingsDataStore.edit { it[GROQ_MODEL] = model }
    }

    suspend fun setAccentColor(color: String) {
        context.settingsDataStore.edit { it[ACCENT_COLOR] = color }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }
}

