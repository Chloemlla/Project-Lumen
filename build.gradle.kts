plugins {
    // CRooot is checked out as a composite build in CI and uses AGP 9.3.1.
    // Keep the root plugin version aligned so Gradle does not load two AGP versions.
    id("com.android.application") version "9.3.1" apply false
    id("com.android.test") version "9.3.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-2.0.1" apply false
    id("androidx.baselineprofile") version "1.4.1" apply false
}
