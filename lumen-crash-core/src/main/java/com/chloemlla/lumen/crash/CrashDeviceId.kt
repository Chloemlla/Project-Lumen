package com.chloemlla.lumen.crash

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Device identifier attached to anonymous crash uploads.
 *
 * The ID is *derived* from the device environment instead of drawn at random, so the same
 * physical device resolves to the same value across app reinstalls, data wipes, and OS updates.
 * That is what makes "show every crash from this device" answerable in the crash dashboard and
 * keeps per-group affected-device counts honest.
 *
 * The seed is hashed with SHA-256 and scoped to the host package, so the raw SSAID never leaves
 * the device and two apps on one device never resolve to the same ID. Only stable hardware traits
 * feed the hash — OS version and build fingerprint are deliberately excluded so a system update
 * does not re-identify the device.
 *
 * When the platform reports no usable SSAID the SDK falls back to a persisted random UUID, which
 * keeps IDs unique but no longer stable across reinstalls.
 */
object CrashDeviceId {
    private const val PREFS_NAME = "lumen_crash_sdk"
    private const val KEY_DERIVED_DEVICE_ID = "device_id_derived_v1"
    private const val KEY_RANDOM_DEVICE_ID = "device_installation_id"
    private const val ID_HEX_LENGTH = 32
    private const val HEX_DIGITS = "0123456789abcdef"

    /** SSAID values known to be unset or shared across a whole device batch. */
    private val unusableAndroidIds = setOf("9774d56d682e549c", "android_id")

    @Volatile
    private var cached: String? = null

    @JvmStatic
    fun resolve(context: Context): String {
        cached?.let { return it }
        val appContext = context.applicationContext ?: context
        return synchronized(this) {
            cached ?: resolveLocked(appContext).also { cached = it }
        }
    }

    private fun resolveLocked(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DERIVED_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val derived = derive(
            androidId = readAndroidId(context),
            packageName = context.packageName,
            hardwareTraits = hardwareTraits(),
        )
        if (derived != null) {
            // commit() rather than apply(): resolve() runs while a crashed process is dying, and an
            // unflushed write would hand the next launch a different ID for the same device.
            prefs.edit().putString(KEY_DERIVED_DEVICE_ID, derived).commit()
            return derived
        }

        prefs.getString(KEY_RANDOM_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_RANDOM_DEVICE_ID, created).commit()
        return created
    }

    @SuppressLint("HardwareIds")
    private fun readAndroidId(context: Context): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()

    private fun hardwareTraits(): List<String> = listOf(
        Build.MANUFACTURER,
        Build.BRAND,
        Build.DEVICE,
        Build.BOARD,
        Build.HARDWARE,
        Build.MODEL,
    )

    /**
     * Hashes the device environment into a 32-hex-character ID, or returns null when [androidId]
     * carries no device-specific entropy and a random fallback is the only honest answer.
     */
    internal fun derive(
        androidId: String?,
        packageName: String,
        hardwareTraits: List<String>,
    ): String? {
        val normalized = androidId?.trim()?.lowercase().orEmpty()
        if (!isUsableAndroidId(normalized)) return null
        val seed = (listOf(normalized, packageName.trim()) + hardwareTraits.map { it.trim() })
            .joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(ID_HEX_LENGTH) {
            for (index in 0 until ID_HEX_LENGTH / 2) {
                val value = digest[index].toInt() and 0xFF
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }
    }

    internal fun isUsableAndroidId(normalized: String): Boolean {
        if (normalized.isEmpty()) return false
        if (normalized in unusableAndroidIds) return false
        // Some ROMs report a padded placeholder instead of omitting the value.
        return normalized.any { it != '0' }
    }
}
