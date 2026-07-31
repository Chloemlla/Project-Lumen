package com.projectlumen.app.core.update

private val supportedReleaseAbis = setOf("arm64_v8a", "x86_64")
private val explicitUnsupportedAbiPattern = Regex(
    """(?:^|_)(?:armeabi(?:_v7a)?|x86(?!_64)|mips(?:64)?)(?:_|$)""",
)

/**
 * Selects an APK that is known to run on the device. Release artifacts are currently
 * 64-bit only, so an unsupported (for example, 32-bit-only) device must not receive
 * a guessed universal or first-listed asset.
 */
internal fun selectBestReleaseAsset(
    assets: List<ReleaseAsset>,
    deviceAbis: List<String>,
): ReleaseAsset? {
    val preferredAbis = deviceAbis
        .map(::normalizeAbi)
        .filter { it in supportedReleaseAbis }
        .distinct()
    if (preferredAbis.isEmpty()) return null

    val verifiedApks = assets.filter { asset ->
        asset.name.endsWith(".apk", ignoreCase = true) && !asset.sha256.isNullOrBlank()
    }
    if (verifiedApks.isEmpty()) return null

    for (deviceAbi in preferredAbis) {
        val specificAsset = verifiedApks.firstOrNull { asset ->
            assetMatchesAbi(asset, deviceAbi)
        }
        if (specificAsset != null) return specificAsset
    }

    return verifiedApks.firstOrNull(::isUniversalAsset)
}

internal fun isSupportedReleaseDevice(deviceAbis: List<String>): Boolean {
    return firstSupportedReleaseAbi(deviceAbis) != null
}

internal fun firstSupportedReleaseAbi(deviceAbis: List<String>): String? {
    return deviceAbis
        .map(::normalizeAbi)
        .firstOrNull { it in supportedReleaseAbis }
}

private fun assetMatchesAbi(asset: ReleaseAsset, requestedAbi: String): Boolean {
    val declaredAbi = asset.abi
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::normalizeAbi)
    if (declaredAbi != null) return declaredAbi == requestedAbi

    val normalizedName = normalizeName(asset.name)
    return normalizedName.contains(requestedAbi)
}

private fun isUniversalAsset(asset: ReleaseAsset): Boolean {
    val declaredAbi = asset.abi
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::normalizeAbi)
    if (declaredAbi == "universal" || declaredAbi == "all") return true
    if (declaredAbi != null) return false

    val normalizedName = normalizeName(asset.name)
    if (normalizedName.contains("universal") ||
        normalizedName.contains("_all_") ||
        normalizedName.startsWith("all_") ||
        normalizedName.endsWith("_all")
    ) {
        return true
    }

    // The release workflow's aggregate APK intentionally has no ABI in its
    // metadata or filename (for example, Project-Lumen_android_1.0.1.apk).
    // Treat that shape as universal, while refusing filenames that explicitly
    // identify a known unsupported 32-bit ABI.
    return supportedReleaseAbis.none { normalizedName.contains(it) } &&
        !hasExplicitUnsupportedAbiToken(normalizedName)
}

private fun hasExplicitUnsupportedAbiToken(normalizedName: String): Boolean {
    return explicitUnsupportedAbiPattern.containsMatchIn(normalizedName)
}

private fun normalizeName(value: String): String {
    return value.lowercase()
        .replace('-', '_')
        .replace('.', '_')
        .replace(' ', '_')
}

private fun normalizeAbi(value: String): String {
    return value.trim()
        .lowercase()
        .replace('-', '_')
        .replace('.', '_')
}
