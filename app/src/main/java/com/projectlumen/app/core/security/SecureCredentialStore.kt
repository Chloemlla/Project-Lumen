package com.projectlumen.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.projectlumen.app.core.api.AuthSession
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import com.tencent.mmkv.MMKV
import java.security.MessageDigest
import java.security.SecureRandom

data class DeviceInstallProfile(
    val hadDeviceCredentialBeforeLaunch: Boolean,
    val firstSeenAt: Long,
    val packageFirstInstallAt: Long,
    val onboardingCompletedAt: Long,
    val ossNoticeCompletedAt: Long,
    val lastAcknowledgedCommitHash: String,
    val lastAcknowledgedBuildTimeUtcMillis: Long,
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
    private val secureMetadata: SharedPreferences? by lazy { createSecureMetadata() }
    private val credentialStore: MMKV? by lazy { createCredentialStore() }

    @Volatile
    private var secureMetadataReset = false

    @Volatile
    private var cryptKeyRotated = false

    @Volatile
    private var legacyMigrationComplete = false

    @Volatile
    private var cachedDeviceInstallationId: String? = null

    /** True when the keystore-backed store could not be opened, so nothing durable can be stored. */
    val degraded: Boolean get() = credentialStore == null

    fun save(session: AuthSession) {
        val store = store() ?: return
        runCatching {
            store.encode(KEY_ACCESS_TOKEN, session.accessToken)
            store.encode(KEY_REFRESH_TOKEN, session.refreshToken)
            store.encode(KEY_TOKEN_TYPE, session.tokenType)
            store.encode(KEY_EXPIRES_AT, session.expiresAt)
            store.encode(KEY_REFRESH_EXPIRES_AT, session.refreshExpiresAt)
            store.encode(KEY_USER_ID, session.user.id)
            store.encode(KEY_USER_EMAIL, session.user.email)
        }.onFailure { error -> Log.e(TAG, "Unable to persist the auth session", error) }
    }

    fun load(): StoredAuthSession? {
        val store = store() ?: return null
        return runCatching {
            val accessToken = store.decodeString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }
            val refreshToken = store.decodeString(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }
            if (accessToken == null || refreshToken == null) return@runCatching null
            StoredAuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = store.decodeString(KEY_TOKEN_TYPE, "Bearer").orEmpty().ifBlank { "Bearer" },
                expiresAt = store.decodeLong(KEY_EXPIRES_AT, 0L),
                refreshExpiresAt = store.decodeLong(KEY_REFRESH_EXPIRES_AT, 0L),
                userId = store.decodeString(KEY_USER_ID).orEmpty(),
                userEmail = store.decodeString(KEY_USER_EMAIL).orEmpty(),
            )
        }.getOrElse { error ->
            Log.e(TAG, "Unable to read the auth session", error)
            null
        }
    }

    fun clear() {
        val store = store() ?: return
        runCatching {
            store.removeValuesForKeys(
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
                    KEY_REMOTE_FRAME_UPLOAD_CONSENT_AT,
                ),
            )
        }.onFailure { error -> Log.e(TAG, "Unable to clear the auth session", error) }
    }

    fun remoteSyncCursor(): Long = readLong(KEY_REMOTE_SYNC_CURSOR)

    fun saveRemoteSyncCursor(cursor: Long) {
        writeLong(KEY_REMOTE_SYNC_CURSOR, cursor)
    }

    fun remoteConfigCursor(): Long = readLong(KEY_REMOTE_CONFIG_CURSOR)

    fun saveRemoteConfigCursor(cursor: Long) {
        writeLong(KEY_REMOTE_CONFIG_CURSOR, cursor)
    }

    /**
     * Timestamp of the user's explicit consent to upload camera frames to the backend analyzer.
     * Zero means no consent: local monitoring toggles never stand in for it.
     */
    fun remoteFrameUploadConsentGrantedAt(): Long = readLong(KEY_REMOTE_FRAME_UPLOAD_CONSENT_AT)

    fun setRemoteFrameUploadConsent(granted: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        writeLong(KEY_REMOTE_FRAME_UPLOAD_CONSENT_AT, if (granted) nowMillis.coerceAtLeast(1L) else 0L)
    }

    fun installProfile(nowMillis: Long = System.currentTimeMillis()): DeviceInstallProfile {
        return runCatching {
            val store = store() ?: error("Secure credential store is unavailable")
            val hadDeviceCredential = store.containsKey(KEY_DEVICE_INSTALLATION_ID)
            val firstSeenAt = store.decodeLong(KEY_FIRST_SEEN_AT, 0L).takeIf { it > 0L }
                ?: nowMillis.also { store.encode(KEY_FIRST_SEEN_AT, it) }
            DeviceInstallProfile(
                hadDeviceCredentialBeforeLaunch = hadDeviceCredential,
                firstSeenAt = firstSeenAt,
                packageFirstInstallAt = packageFirstInstallAt(),
                onboardingCompletedAt = store.decodeLong(KEY_ONBOARDING_COMPLETED_AT, 0L),
                ossNoticeCompletedAt = store.decodeLong(KEY_OSS_NOTICE_COMPLETED_AT, 0L),
                lastAcknowledgedCommitHash = store.decodeString(KEY_BUILD_UPDATE_NOTES_ACK_COMMIT).orEmpty(),
                lastAcknowledgedBuildTimeUtcMillis = store.decodeLong(
                    KEY_BUILD_UPDATE_NOTES_ACK_BUILD_TIME,
                    0L,
                ),
            )
        }.getOrElse { error ->
            Log.e(TAG, "installProfile failed; using ephemeral defaults", error)
            DeviceInstallProfile(
                hadDeviceCredentialBeforeLaunch = false,
                firstSeenAt = nowMillis,
                packageFirstInstallAt = packageFirstInstallAt(),
                onboardingCompletedAt = 0L,
                ossNoticeCompletedAt = 0L,
                lastAcknowledgedCommitHash = "",
                lastAcknowledgedBuildTimeUtcMillis = 0L,
            )
        }
    }

    fun markOnboardingCompleted(nowMillis: Long = System.currentTimeMillis()) {
        writeLong(KEY_ONBOARDING_COMPLETED_AT, nowMillis.coerceAtLeast(1L))
    }

    fun markOssNoticeCompleted(nowMillis: Long = System.currentTimeMillis()) {
        writeLong(KEY_OSS_NOTICE_COMPLETED_AT, nowMillis.coerceAtLeast(1L))
    }

    fun resetOnboardingCompletion() {
        writeLong(KEY_ONBOARDING_COMPLETED_AT, 0L)
    }

    fun markBuildUpdateNotesAcknowledged(
        commitHash: String,
        buildTimeUtcMillis: Long,
    ) {
        require(commitHash.isNotBlank()) { "Build acknowledgment requires a commit hash" }
        require(buildTimeUtcMillis > 0L) { "Build acknowledgment requires a positive build time" }
        val store = store() ?: return
        runCatching {
            store.encode(KEY_BUILD_UPDATE_NOTES_ACK_COMMIT, commitHash)
            store.encode(KEY_BUILD_UPDATE_NOTES_ACK_BUILD_TIME, buildTimeUtcMillis)
        }.onFailure { error -> Log.e(TAG, "Unable to persist the build acknowledgment", error) }
    }

    fun deviceInstallationId(): String {
        cachedDeviceInstallationId?.let { return it }
        return runCatching {
            val store = store() ?: error("Secure credential store is unavailable")
            val existing = store.decodeString(KEY_DEVICE_INSTALLATION_ID)
                ?.takeIf { it.isNotBlank() }
            if (
                existing != null &&
                isDeviceFingerprint(existing) &&
                store.decodeInt(KEY_DEVICE_FINGERPRINT_VERSION, 0) >= DEVICE_FINGERPRINT_VERSION
            ) {
                return@runCatching existing
            }
            val generated = generateDeviceFingerprint()
            store.encode(KEY_DEVICE_INSTALLATION_ID, generated)
            store.encode(KEY_DEVICE_FINGERPRINT_VERSION, DEVICE_FINGERPRINT_VERSION)
            generated
        }.onSuccess { resolved ->
            cachedDeviceInstallationId = resolved
        }.getOrElse { error ->
            Log.e(TAG, "deviceInstallationId failed; using ephemeral fingerprint", error)
            generateDeviceFingerprint()
        }
    }

    private fun readLong(key: String): Long {
        val store = store() ?: return 0L
        return runCatching { store.decodeLong(key, 0L) }.getOrDefault(0L).coerceAtLeast(0L)
    }

    private fun writeLong(key: String, value: Long) {
        val store = store() ?: return
        runCatching { store.encode(key, value.coerceAtLeast(0L)) }
            .onFailure { error -> Log.e(TAG, "Unable to persist $key", error) }
    }

    private fun store(): MMKV? {
        val store = credentialStore ?: return null
        migrateLegacyCredentialsIfNeeded(store)
        return store
    }

    private fun createSecureMetadata(): SharedPreferences? {
        return runCatching { encryptedPreferences() }.getOrElse { error ->
            Log.e(TAG, "Encrypted metadata unavailable; recreating the store", error)
            runCatching {
                appContext.deleteSharedPreferences(STORE_NAME)
                secureMetadataReset = true
                encryptedPreferences()
            }.getOrElse { retryError ->
                Log.e(TAG, "Encrypted metadata could not be recreated", retryError)
                null
            }
        }
    }

    private fun encryptedPreferences(): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        STORE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun createCredentialStore(): MMKV? {
        val cryptKey = mmkvCryptKey() ?: return null
        return runCatching { ProjectLumenMmkv.encryptedMmkvWithId(STORE_NAME, cryptKey) }
            .onFailure { error -> Log.e(TAG, "Encrypted credential store unavailable", error) }
            .getOrNull()
            ?.also { store ->
                if (!cryptKeyRotated) return@also
                // The previous key is gone with the recreated metadata file, so the existing
                // content can never be decrypted again; keeping it would serve garbage.
                Log.w(TAG, "Credential encryption key rotated; clearing the unreadable store")
                runCatching { store.clearAll() }
            }
    }

    private fun migrateLegacyCredentialsIfNeeded(store: MMKV) {
        if (legacyMigrationComplete) return
        if (runCatching { store.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false) }.getOrDefault(false)) {
            legacyMigrationComplete = true
            return
        }
        synchronized(migrationLock) {
            if (legacyMigrationComplete) return
            runCatching { migrateLegacyCredentials(store) }
                .onFailure { error -> Log.e(TAG, "Legacy credential migration failed", error) }
            // Mark the migration done either way: retrying it on every launch cannot succeed
            // once the keystore-backed legacy file is unreadable.
            runCatching { store.encode(KEY_MMKV_MIGRATION_COMPLETE, true) }
            legacyMigrationComplete = true
        }
    }

    private fun migrateLegacyCredentials(store: MMKV) {
        val metadata = secureMetadata ?: return
        val legacyAccessToken = metadata.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        val legacyRefreshToken = metadata.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
        if (!hasStoredSession(store) && legacyAccessToken != null && legacyRefreshToken != null) {
            store.encode(KEY_ACCESS_TOKEN, legacyAccessToken)
            store.encode(KEY_REFRESH_TOKEN, legacyRefreshToken)
            store.encode(
                KEY_TOKEN_TYPE,
                metadata.getString(KEY_TOKEN_TYPE, "Bearer").orEmpty().ifBlank { "Bearer" },
            )
            store.encode(KEY_EXPIRES_AT, metadata.getLong(KEY_EXPIRES_AT, 0L))
            store.encode(KEY_REFRESH_EXPIRES_AT, metadata.getLong(KEY_REFRESH_EXPIRES_AT, 0L))
        }

        val legacyDeviceId = metadata.getString(KEY_DEVICE_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }
        if (!store.containsKey(KEY_DEVICE_INSTALLATION_ID) && legacyDeviceId != null) {
            store.encode(KEY_DEVICE_INSTALLATION_ID, legacyDeviceId)
        }

        metadata.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_TYPE)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_REFRESH_EXPIRES_AT)
            .remove(KEY_DEVICE_INSTALLATION_ID)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    private fun hasStoredSession(store: MMKV): Boolean {
        return store.decodeString(KEY_ACCESS_TOKEN)?.isNotBlank() == true &&
            store.decodeString(KEY_REFRESH_TOKEN)?.isNotBlank() == true
    }

    private fun mmkvCryptKey(): String? {
        val metadata = secureMetadata ?: return null
        val existing = runCatching { metadata.getString(KEY_MMKV_CRYPT_KEY, null) }.getOrNull()
        if (!existing.isNullOrBlank()) return existing
        val generated = generateCryptKey()
        val persisted = runCatching {
            metadata.edit().putString(KEY_MMKV_CRYPT_KEY, generated).commit()
        }.getOrDefault(false)
        if (!persisted) {
            Log.e(TAG, "Unable to persist the credential encryption key; storage stays unavailable")
            return null
        }
        cryptKeyRotated = secureMetadataReset
        return generated
    }

    private fun generateCryptKey(): String {
        // MMKV's AES layer consumes only the first 16 bytes of the key, so anything past that is
        // discarded: keep every character random instead of padding with UUID structure.
        val random = SecureRandom()
        val key = CharArray(MMKV_CRYPT_KEY_LENGTH) {
            CRYPT_KEY_ALPHABET[random.nextInt(CRYPT_KEY_ALPHABET.length)]
        }
        return String(key)
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
        private const val TAG = "SecureCredentialStore"
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
        private const val KEY_REMOTE_FRAME_UPLOAD_CONSENT_AT = "remote_frame_upload_consent_at"
        private const val KEY_DEVICE_INSTALLATION_ID = "device_installation_id"
        private const val KEY_DEVICE_FINGERPRINT_VERSION = "device_fingerprint_version"
        private const val KEY_FIRST_SEEN_AT = "first_seen_at"
        private const val KEY_ONBOARDING_COMPLETED_AT = "onboarding_completed_at"
        private const val KEY_OSS_NOTICE_COMPLETED_AT = "oss_notice_completed_at"
        private const val KEY_BUILD_UPDATE_NOTES_ACK_COMMIT = "build_update_notes_ack_commit"
        private const val KEY_BUILD_UPDATE_NOTES_ACK_BUILD_TIME = "build_update_notes_ack_build_time"
        private const val KEY_MMKV_CRYPT_KEY = "mmkv_crypt_key"
        private const val KEY_MMKV_MIGRATION_COMPLETE = "mmkv_migration_complete"
        private const val DEVICE_FINGERPRINT_LENGTH = 64
        private const val DEVICE_FINGERPRINT_VERSION = 2
        private const val MMKV_CRYPT_KEY_LENGTH = 16
        private const val CRYPT_KEY_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
