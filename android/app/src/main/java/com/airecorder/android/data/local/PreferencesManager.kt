package com.airecorder.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    
    suspend fun getServerUrl(): String {
        return context.dataStore.data.map { 
            it[PreferencesKeys.SERVER_URL] ?: ""
        }.let { flow ->
            var result = ""
            flow.collect { result = it }
            result
        }
    }
    
    suspend fun getApiToken(): String {
        return context.dataStore.data.map { 
            it[PreferencesKeys.API_TOKEN] ?: ""
        }.let { flow ->
            var result = ""
            flow.collect { result = it }
            result
        }
    }
}
