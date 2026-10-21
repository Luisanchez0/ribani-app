package com.ribani.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ribani_session")

data class SessionUser(
    val id: String,
    val nombre: String,
    val apellido: String,
    val email: String,
    val telefono: String?,
    val rolCodigo: String,
    val twoFactorEnabled: Boolean = false,
)

class SessionManager(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NOMBRE = stringPreferencesKey("user_nombre")
        val USER_APELLIDO = stringPreferencesKey("user_apellido")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_TELEFONO = stringPreferencesKey("user_telefono")
        val USER_ROL = stringPreferencesKey("user_rol")
        val TWO_FACTOR_ENABLED = stringPreferencesKey("two_factor_enabled")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[Keys.ACCESS_TOKEN].isNullOrBlank()
    }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        user: SessionUser,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USER_NOMBRE] = user.nombre
            prefs[Keys.USER_APELLIDO] = user.apellido
            prefs[Keys.USER_EMAIL] = user.email
            prefs[Keys.USER_TELEFONO] = user.telefono.orEmpty()
            prefs[Keys.USER_ROL] = user.rolCodigo
            prefs[Keys.TWO_FACTOR_ENABLED] = user.twoFactorEnabled.toString()
        }
    }

    suspend fun updateAccessToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = token
        }
    }

    suspend fun updateUser(user: SessionUser) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USER_NOMBRE] = user.nombre
            prefs[Keys.USER_APELLIDO] = user.apellido
            prefs[Keys.USER_EMAIL] = user.email
            prefs[Keys.USER_TELEFONO] = user.telefono.orEmpty()
            prefs[Keys.USER_ROL] = user.rolCodigo
            prefs[Keys.TWO_FACTOR_ENABLED] = user.twoFactorEnabled.toString()
        }
    }

    suspend fun accessToken(): String? {
        return context.dataStore.data.first()[Keys.ACCESS_TOKEN]
    }

    suspend fun refreshToken(): String? {
        return context.dataStore.data.first()[Keys.REFRESH_TOKEN]
    }

    suspend fun currentUser(): SessionUser? {
        val prefs = context.dataStore.data.first()
        val id = prefs[Keys.USER_ID] ?: return null
        return SessionUser(
            id = id,
            nombre = prefs[Keys.USER_NOMBRE].orEmpty(),
            apellido = prefs[Keys.USER_APELLIDO].orEmpty(),
            email = prefs[Keys.USER_EMAIL].orEmpty(),
            telefono = prefs[Keys.USER_TELEFONO]?.takeIf { it.isNotBlank() },
            rolCodigo = prefs[Keys.USER_ROL].orEmpty(),
            twoFactorEnabled = prefs[Keys.TWO_FACTOR_ENABLED] == "true",
        )
    }

    suspend fun setTwoFactorEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TWO_FACTOR_ENABLED] = enabled.toString()
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
