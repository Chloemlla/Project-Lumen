package com.projectlumen.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAssetSelectorTest {
    @Test
    fun thirtyTwoBitOnlyDeviceDoesNotReceiveAnApk() {
        assertNull(
            selectBestReleaseAsset(
                assets = listOf(universalAsset(), arm64Asset()),
                deviceAbis = listOf("armeabi-v7a", "x86"),
            ),
        )
    }

    @Test
    fun unknownNonUniversalDeviceDoesNotReceiveAnApk() {
        assertNull(
            selectBestReleaseAsset(
                assets = listOf(universalAsset()),
                deviceAbis = listOf("mips64"),
            ),
        )
    }

    @Test
    fun supportedAbiSelectionSkips32BitEntriesBefore64BitEntry() {
        assertEquals(
            "arm64_v8a",
            firstSupportedReleaseAbi(listOf("armeabi-v7a", "arm64-v8a")),
        )
    }

    @Test
    fun arm64SpecificAssetWinsOverUniversal() {
        val selected = selectBestReleaseAsset(
            assets = listOf(universalAsset(), arm64Asset()),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals("Project-Lumen_arm64-v8a.apk", selected?.name)
    }

    @Test
    fun x8664UsesUniversalWhenSpecificAssetIsAbsent() {
        val selected = selectBestReleaseAsset(
            assets = listOf(universalAsset(), arm64Asset()),
            deviceAbis = listOf("x86_64"),
        )

        assertEquals("Project-Lumen_universal.apk", selected?.name)
    }

    @Test
    fun x8664SpecificAssetIsRecognizedFromFilename() {
        val selected = selectBestReleaseAsset(
            assets = listOf(
                universalAsset(),
                ReleaseAsset(
                    name = "Project-Lumen_x86_64.apk",
                    downloadUrl = "https://example.invalid/x86_64.apk",
                    sha256 = SHA256,
                ),
            ),
            deviceAbis = listOf("x86-64"),
        )

        assertEquals("Project-Lumen_x86_64.apk", selected?.name)
    }

    @Test
    fun aggregateApkWithoutAbiMetadataIsTreatedAsUniversal() {
        val selected = selectBestReleaseAsset(
            assets = listOf(
                ReleaseAsset(
                    name = "Project-Lumen_android_1.0.1-a1b2c3d4.apk",
                    downloadUrl = "https://example.invalid/aggregate.apk",
                    sha256 = SHA256,
                ),
            ),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals("Project-Lumen_android_1.0.1-a1b2c3d4.apk", selected?.name)
    }

    @Test
    fun aggregateApkWithBlankAbiMetadataIsTreatedAsUniversal() {
        val selected = selectBestReleaseAsset(
            assets = listOf(
                ReleaseAsset(
                    name = "Project-Lumen_android_1.0.1-a1b2c3d4.apk",
                    downloadUrl = "https://example.invalid/aggregate.apk",
                    sha256 = SHA256,
                    abi = "   ",
                ),
            ),
            deviceAbis = listOf("x86_64"),
        )

        assertEquals("Project-Lumen_android_1.0.1-a1b2c3d4.apk", selected?.name)
    }

    @Test
    fun explicit32BitFilenameIsNotReclassifiedAsUniversal() {
        val selected = selectBestReleaseAsset(
            assets = listOf(
                ReleaseAsset(
                    name = "Project-Lumen_android_1.0.1_armeabi-v7a.apk",
                    downloadUrl = "https://example.invalid/armeabi-v7a.apk",
                    sha256 = SHA256,
                ),
            ),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(selected)
    }

    @Test
    fun unsupportedDeclaredAbiCannotBeRescuedByUniversalFilename() {
        val selected = selectBestReleaseAsset(
            assets = listOf(
                ReleaseAsset(
                    name = "Project-Lumen_universal.apk",
                    downloadUrl = "https://example.invalid/universal.apk",
                    sha256 = SHA256,
                    abi = "armeabi-v7a",
                ),
            ),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(selected)
    }

    @Test
    fun explicitArmv7FilenameIsNotReclassifiedAsUniversal() {
        val selected = selectBestReleaseAsset(
            assets = listOf(
                ReleaseAsset(
                    name = "Project-Lumen_android_1.0.1_armv7.apk",
                    downloadUrl = "https://example.invalid/armv7.apk",
                    sha256 = SHA256,
                ),
            ),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(selected)
    }

    private fun universalAsset(): ReleaseAsset = ReleaseAsset(
        name = "Project-Lumen_universal.apk",
        downloadUrl = "https://example.invalid/universal.apk",
        sha256 = SHA256,
        abi = "universal",
    )

    private fun arm64Asset(): ReleaseAsset = ReleaseAsset(
        name = "Project-Lumen_arm64-v8a.apk",
        downloadUrl = "https://example.invalid/arm64-v8a.apk",
        sha256 = SHA256,
        abi = "arm64-v8a",
    )

    private companion object {
        const val SHA256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
