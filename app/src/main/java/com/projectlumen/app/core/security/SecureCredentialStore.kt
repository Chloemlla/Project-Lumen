package com.projectlumen.app.core.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.projectlumen.app.core.api.AuthSession
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import java.security.MessageDigest
import java.util.UUID

data class DeviceInstallProfile(
    val hadDeviceCredentialBeforeLaunch: Boolean,
    val firstSeenAt: Long,
    val packageFirstInstallAt: Long,
    val onboardingCompletedAt: Long,
)

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
                KEY_REMOTE_CONFIG_CURSOR,
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

    fun remoteConfigCursor(): Long {
        migrateLegacyCredentialsIfNeeded()
        return encryptedMmkv.decodeLong(KEY_REMOTE_CONFIG_CURSOR, 0L).coerceAtLeast(0L)
    }

    fun saveRemoteConfigCursor(cursor: Long) {
        migrateLegacyCredentialsIfNeeded()
        encryptedMmkv.encode(KEY_REMOTE_CONFIG_CURSOR, cursor.coerceAtLeast(0L))
    }

    fun installProfile(nowMillis: Long = System.currentTimeMillis()): DeviceInstallProfile {
        migrateLegacyCredentialsIfNeeded()
        val hadDeviceCredential = encryptedMmkv.containsKey(KEY_DEVICE_INSTALLATION_ID)
        val firstSeenAt = encryptedMmkv.decodeLong(KEY_FIRST_SEEN_AT, 0L).takeIf { it > 0L }
            ?: nowMillis.also { encryptedMmkv.encode(KEY_FIRST_SEEN_AT, it) }
        return DeviceInstallProfile(
            hadDeviceCredentialBeforeLaunch = hadDeviceCredential,
            firstSeenAt = firstSeenAt,
            packageFirstInstallAt = packageFirstInstallAt(),
            onboardingCompletedAt = encryptedMmkv.decodeLong(KEY_ONBOARDING_COMPLETED_AT, 0L),
        )
    }

    fun markOnboardingCompleted(nowMillis: Long = System.currentTimeMillis()) {
        migrateLegacyCredentialsIfNeeded()
        encryptedMmkv.encode(KEY_ONBOARDING_COMPLETED_AT, nowMillis.coerceAtLeast(1L))
    }

    fun deviceInstallationId(): String {
        migrateLegacyCredentialsIfNeeded()
        val existing = encryptedMmkv.decodeString(KEY_DEVICE_INSTALLATION_ID)
            ?.takeIf { it.isNotBlank() }
        if (
            isDeviceFingerprint(existing) &&
            encryptedMmkv.decodeInt(KEY_DEVICE_FINGERPRINT_VERSION, 0) >= DEVICE_FINGERPRINT_VERSION
        ) {
            return existing
        }
        val generated = generateDeviceFingerprint()
        encryptedMmkv.encode(KEY_DEVICE_INSTALLATION_ID, generated)
        encryptedMmkv.encode(KEY_DEVICE_FINGERPRINT_VERSION, DEVICE_FINGERPRINT_VERSION)
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
        val existing = secureMetadata.getString(KEY_MMKV_CRYPT_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        check(secureMetadata.edit().putString(KEY_MMKV_CRYPT_KEY, generated).commit()) {
            "Unable to persist MMKV encryption key."
        }
        return generated
    }

    private fun generateDeviceFingerprint(): String {
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val material = listOf(
            "project-lumen-device-v2",
            appContext.packageName,
            androidId,
            Build.BRAND.orEmpty(),
            Build.MANUFACTURER.orEmpty(),
            Build.MODEL.orEmpty(),
            Build.DEVICE.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.VERSION.RELEASE.orEmpty(),
            Build.VERSION.SDK_INT.toString(),
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            Build.FINGERPRINT.orEmpty(),
        ).joinToString("|") { it.trim().lowercase() }
        return sha256Hex(material)
    }

    private fun packageFirstInstallAt(): Long {
        return runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
        }.getOrDefault(0L)
    }

    private fun isDeviceFingerprint(value: String?): Boolean {
        return value?.length == DEVICE_FINGERPRINT_LENGTH &&
            value.all { it in '0'..'9' || it in 'a'..'f' }
    }

    private fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val output = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val unsigned = byte.toInt() and 0xff
            output[index * 2] = HEX_CHARS[unsigned ushr 4]
            output[index * 2 + 1] = HEX_CHARS[unsigned and 0x0f]
        }
        return String(output)
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
        private const val KEY_REMOTE_CONFIG_CURSOR = "remote_config_cursor"
        private const val KEY_DEVICE_INSTALLATION_ID = "device_installation_id"
        private const val KEY_DEVICE_FINGERPRINT_VERSION = "device_fingerprint_version"
        private const val KEY_FIRST_SEEN_AT = "first_seen_at"
        private const val KEY_ONBOARDING_COMPLETED_AT = "onboarding_completed_at"
        private const val KEY_MMKV_CRYPT_KEY = "mmkv_crypt_key"
        private const val KEY_MMKV_MIGRATION_COMPLETE = "mmkv_migration_complete"
        private const val DEVICE_FINGERPRINT_LENGTH = 64
        private const val DEVICE_FINGERPRINT_VERSION = 2
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
