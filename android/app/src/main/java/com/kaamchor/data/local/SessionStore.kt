package com.kaamchor.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

const val DEFAULT_SERVER_URL = "https://kaamchorz.dpdns.org"

class SessionStore(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USER_JSON = stringPreferencesKey("user_json")
    }

    val serverUrl: Flow<String> = context.sessionDataStore.data.map { it[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL }
    val accessToken: Flow<String?> = context.sessionDataStore.data.map { it[Keys.ACCESS] }
    val refreshToken: Flow<String?> = context.sessionDataStore.data.map { it[Keys.REFRESH] }
    val userJson: Flow<String?> = context.sessionDataStore.data.map { it[Keys.USER_JSON] }

    suspend fun saveAuth(access: String, refresh: String, userJson: String) {
        context.sessionDataStore.edit {
            it[Keys.ACCESS] = access
            it[Keys.REFRESH] = refresh
            it[Keys.USER_JSON] = userJson
        }
    }

    suspend fun saveUser(userJson: String) {
        context.sessionDataStore.edit { it[Keys.USER_JSON] = userJson }
    }

    suspend fun setServerUrl(url: String) {
        context.sessionDataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun clear() {
        context.sessionDataStore.edit {
            it.remove(Keys.ACCESS)
            it.remove(Keys.REFRESH)
            it.remove(Keys.USER_JSON)
        }
    }

    fun serverUrlBlocking(): String = runBlocking { serverUrl.first() }
    fun accessTokenBlocking(): String? = runBlocking { accessToken.first() }
    fun refreshTokenBlocking(): String? = runBlocking { refreshToken.first() }
}
