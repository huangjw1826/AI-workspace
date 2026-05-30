package com.airecorder.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_TOKEN = stringPreferencesKey("api_token")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val OVERVIEW_EXPANDED = booleanPreferencesKey("overview_expanded")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
    
    val serverUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SERVER_URL] ?: ""
        }
    
    val apiToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.API_TOKEN] ?: ""
        }
    
    val darkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DARK_MODE] ?: false
        }
    
    val overviewExpanded: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.OVERVIEW_EXPANDED] ?: true
        }
    
    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
        }
    
    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = url
        }
    }
    
    suspend fun setApiToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_TOKEN] = token
        }
    }
    
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = enabled
        }
    }
    
    suspend fun setOverviewExpanded(expanded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERVIEW_EXPANDED] = expanded
        }
    }
    
    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }
    
    suspend fun getServerUrl(): String {
        return serverUrl.first()
    }
    
    suspend fun getApiToken(): String {
        return apiToken.first()
    }
}
