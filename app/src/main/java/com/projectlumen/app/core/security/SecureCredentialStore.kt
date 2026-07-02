package com.projectlumen.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.projectlumen.app.core.api.AuthSession
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import java.util.UUID

data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresAt: Long,
    val refreshExpiresAt: Long,
    val userId: String,
    val userEmail: String,
)

class SecureCredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val migrationLock = Any()
    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    private val secureMetadata by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            STORE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val encryptedMmkv by lazy {
        ProjectLumenMmkv.encryptedMmkvWithId(STORE_NAME, mmkvCryptKey())
    }

    fun save(session: AuthSession) {
        migrateLegacyCredentialsIfNeeded()
        encryptedMmkv.encode(KEY_ACCESS_TOKEN, session.accessToken)
        encryptedMmkv.encode(KEY_REFRESH_TOKEN, session.refreshToken)
        encryptedMmkv.encode(KEY_TOKEN_TYPE, session.tokenType)
        encryptedMmkv.encode(KEY_EXPIRES_AT, session.expiresAt)
        encryptedMmkv.encode(KEY_REFRESH_EXPIRES_AT, session.refreshExpiresAt)
        encryptedMmkv.encode(KEY_USER_ID, session.user.id)
        encryptedMmkv.encode(KEY_USER_EMAIL, session.user.email)
    }

    fun load(): StoredAuthSession? {
        migrateLegacyCredentialsIfNeeded()
        val accessToken = encryptedMmkv.decodeString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }
        val refreshToken = encryptedMmkv.decodeString(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }
        if (accessToken == null || refreshToken == null) return null
        return StoredAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = encryptedMmkv.decodeString(KEY_TOKEN_TYPE, "Bearer").orEmpty().ifBlank { "Bearer" },
            expiresAt = encryptedMmkv.decodeLong(KEY_EXPIRES_AT, 0L),
            refreshExpiresAt = encryptedMmkv.decodeLong(KEY_REFRESH_EXPIRES_AT, 0L),
            userId = encryptedMmkv.decodeString(KEY_USER_ID).orEmpty(),
            userEmail = encryptedMmkv.decodeString(KEY_USER_EMAIL).orEmpty(),
        )
    }

    fun clear() {
        migrateLegacyCredentialsIfNeeded()
        encryptedMmkv.removeValuesForKeys(
            arrayOf(
                KEY_ACCESS_TOKEN,
                KEY_REFRESH_TOKEN,
                KEY_TOKEN_TYPE,
                KEY_EXPIRES_AT,
                KEY_REFRESH_EXPIRES_AT,
                KEY_USER_ID,
                KEY_USER_EMAIL,
                KEY_REMOTE_SYNC_CURSOR,
            ),
        )
    }

    fun remoteSyncCursor(): Long {
        migrateLegacyCredentialsIfNeeded()
        return encryptedMmkv.decodeLong(KEY_REMOTE_SYNC_CURSOR, 0L).coerceAtLeast(0L)
    }

    fun saveRemoteSyncCursor(cursor: Long) {
        migrateLegacyCredentialsIfNeeded()
        encryptedMmkv.encode(KEY_REMOTE_SYNC_CURSOR, cursor.coerceAtLeast(0L))
    }

    fun deviceInstallationId(seed: String? = null): String {
        migrateLegacyCredentialsIfNeeded()
        val existing = encryptedMmkv.decodeString(KEY_DEVICE_INSTALLATION_ID)
            ?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = seed?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        encryptedMmkv.encode(KEY_DEVICE_INSTALLATION_ID, generated)
        return generated
    }

    private fun migrateLegacyCredentialsIfNeeded() {
        if (encryptedMmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return
        synchronized(migrationLock) {
            if (encryptedMmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return

            val legacyAccessToken = secureMetadata.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
            val legacyRefreshToken = secureMetadata.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
            if (!hasStoredSession() && legacyAccessToken != null && legacyRefreshToken != null) {
                encryptedMmkv.encode(KEY_ACCESS_TOKEN, legacyAccessToken)
                encryptedMmkv.encode(KEY_REFRESH_TOKEN, legacyRefreshToken)
                encryptedMmkv.encode(
                    KEY_TOKEN_TYPE,
                    secureMetadata.getString(KEY_TOKEN_TYPE, "Bearer").orEmpty().ifBlank { "Bearer" },
                )
                encryptedMmkv.encode(KEY_EXPIRES_AT, secureMetadata.getLong(KEY_EXPIRES_AT, 0L))
                encryptedMmkv.encode(KEY_REFRESH_EXPIRES_AT, secureMetadata.getLong(KEY_REFRESH_EXPIRES_AT, 0L))
            }

            val legacyDeviceId = secureMetadata.getString(KEY_DEVICE_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }
            if (!encryptedMmkv.containsKey(KEY_DEVICE_INSTALLATION_ID) && legacyDeviceId != null) {
                encryptedMmkv.encode(KEY_DEVICE_INSTALLATION_ID, legacyDeviceId)
            }

            secureMetadata.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_TYPE)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_REFRESH_EXPIRES_AT)
                .remove(KEY_DEVICE_INSTALLATION_ID)
                .remove(KEY_USER_ID)
                .remove(KEY_USER_EMAIL)
                .apply()
            encryptedMmkv.encode(KEY_MMKV_MIGRATION_COMPLETE, true)
        }
    }

    private fun hasStoredSession(): Boolean {
        return encryptedMmkv.decodeString(KEY_ACCESS_TOKEN)?.isNotBlank() == true &&
            encryptedMmkv.decodeString(KEY_REFRESH_TOKEN)?.isNotBlank() == true
    }

    private fun mmkvCryptKey(): String {
        val existing = secureMetadata.getString(KEY_MMKV_CRYPT_KEY, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        secureMetadata.edit().putString(KEY_MMKV_CRYPT_KEY, generated).apply()
        return generated
    }

    private companion object {
        private const val STORE_NAME = "project_lumen_secure_credentials"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_REMOTE_SYNC_CURSOR = "remote_sync_cursor"
        private const val KEY_DEVICE_INSTALLATION_ID = "device_installation_id"
        private const val KEY_MMKV_CRYPT_KEY = "mmkv_crypt_key"
        private const val KEY_MMKV_MIGRATION_COMPLETE = "mmkv_migration_complete"
    }
}
