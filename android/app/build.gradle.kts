import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val signingProperties = Properties()
val signingPropertiesFile = rootProject.file("gradle.properties")
if (signingPropertiesFile.exists()) {
    signingPropertiesFile.inputStream().use { signingProperties.load(it) }
}

fun trimmedSigningProperty(name: String): String =
    signingProperties.getProperty(name)?.trim().orEmpty()

fun rawSigningProperty(name: String): String =
    signingProperties.getProperty(name).orEmpty()

val releaseStoreFile = trimmedSigningProperty("NEXAI_STORE_FILE")
val releaseKeyAlias = trimmedSigningProperty("NEXAI_KEY_ALIAS")

val hasReleaseSigning = trimmedSigningProperty("NEXAI_USE_DEBUG_SIGNING") != "true" &&
    listOf(
        releaseStoreFile,
        rawSigningProperty("NEXAI_STORE_PASSWORD"),
        releaseKeyAlias,
        rawSigningProperty("NEXAI_KEY_PASSWORD"),
    ).all { it.isNotBlank() }

android {
    namespace = "com.projectlumen.project_lumen"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = rawSigningProperty("NEXAI_STORE_PASSWORD")
                keyAlias = releaseKeyAlias
                keyPassword = rawSigningProperty("NEXAI_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.projectlumen.project_lumen"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
