package com.projectlumen.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.projectlumen.app.core.api.AuthSession

data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresAt: Long,
    val refreshExpiresAt: Long,
)

class SecureCredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    private val preferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            STORE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(session: AuthSession) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_TOKEN_TYPE, session.tokenType)
            .putLong(KEY_EXPIRES_AT, session.expiresAt)
            .putLong(KEY_REFRESH_EXPIRES_AT, session.refreshExpiresAt)
            .apply()
    }

    fun load(): StoredAuthSession? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
        if (accessToken == null || refreshToken == null) return null
        return StoredAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = preferences.getString(KEY_TOKEN_TYPE, "Bearer").orEmpty().ifBlank { "Bearer" },
            expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L),
            refreshExpiresAt = preferences.getLong(KEY_REFRESH_EXPIRES_AT, 0L),
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        private const val STORE_NAME = "project_lumen_secure_credentials"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"
    }
}
