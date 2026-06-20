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

val hasReleaseSigning = signingProperties.getProperty("NEXAI_USE_DEBUG_SIGNING") != "true" &&
    listOf(
        "NEXAI_STORE_FILE",
        "NEXAI_STORE_PASSWORD",
        "NEXAI_KEY_ALIAS",
        "NEXAI_KEY_PASSWORD",
    ).all { signingProperties.getProperty(it).isNullOrBlank().not() }

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
                storeFile = file(signingProperties.getProperty("NEXAI_STORE_FILE"))
                storePassword = signingProperties.getProperty("NEXAI_STORE_PASSWORD")
                keyAlias = signingProperties.getProperty("NEXAI_KEY_ALIAS")
                keyPassword = signingProperties.getProperty("NEXAI_KEY_PASSWORD")
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
