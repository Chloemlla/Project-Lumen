plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val projectLumenUseDebugSigning =
    providers.gradleProperty("PROJECT_LUMEN_USE_DEBUG_SIGNING").orNull.toBoolean()
val projectLumenStoreFile = providers.gradleProperty("PROJECT_LUMEN_STORE_FILE").orNull
val projectLumenStorePassword = providers.gradleProperty("PROJECT_LUMEN_STORE_PASSWORD").orNull
val projectLumenKeyAlias = providers.gradleProperty("PROJECT_LUMEN_KEY_ALIAS").orNull
val projectLumenKeyPassword = providers.gradleProperty("PROJECT_LUMEN_KEY_PASSWORD").orNull
val projectLumenReleaseSigningConfigured = listOf(
    projectLumenStoreFile,
    projectLumenStorePassword,
    projectLumenKeyAlias,
    projectLumenKeyPassword,
).all { !it.isNullOrBlank() }

fun projectLumenVersionCodeFromName(versionName: String): Int {
    val parts = versionName
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
    val code = (parts.getOrElse(0) { 0 } * 10_000L) +
        (parts.getOrElse(1) { 0 } * 100L) +
        parts.getOrElse(2) { 0 }
    return code.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

android {
    namespace = "com.projectlumen.app"
    compileSdk = 36

    val projectLumenVersionName = providers.environmentVariable("PROJECT_LUMEN_VERSION_NAME")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: layout.projectDirectory.file("application.version").asFile.readText(Charsets.UTF_8).trim().ifBlank { "1.0.0" }
    val projectLumenVersionCode = providers.environmentVariable("PROJECT_LUMEN_VERSION_CODE")
        .orNull
        ?.toIntOrNull()
        ?: projectLumenVersionCodeFromName(projectLumenVersionName)
    val projectLumenBuildTimeUtcMillis = providers.environmentVariable("PROJECT_LUMEN_BUILD_TIME_UTC_MILLIS")
        .orNull
        ?.toLongOrNull()
        ?: System.currentTimeMillis()
    val projectLumenCommitHash = providers.environmentVariable("PROJECT_LUMEN_COMMIT_HASH")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim().ifBlank { "unknown" }
    val projectLumenShortHash = providers.environmentVariable("PROJECT_LUMEN_SHORT_HASH")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.exec { commandLine("git", "rev-parse", "--short=8", "HEAD") }.standardOutput.asText.get().trim().ifBlank { "unknown" }

    defaultConfig {
        buildConfigField("long", "BUILD_TIME_UTC_MILLIS", "${projectLumenBuildTimeUtcMillis}L")
        buildConfigField("String", "COMMIT_HASH", "\"$projectLumenCommitHash\"")
        buildConfigField("String", "SHORT_HASH", "\"$projectLumenShortHash\"")

        applicationId = "com.projectlumen.app"
        minSdk = 26
        targetSdk = 36
        versionCode = projectLumenVersionCode
        versionName = projectLumenVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (projectLumenReleaseSigningConfigured) {
                storeFile = file(projectLumenStoreFile!!)
                storePassword = projectLumenStorePassword
                keyAlias = projectLumenKeyAlias
                keyPassword = projectLumenKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (projectLumenUseDebugSigning) {
                signingConfig = signingConfigs.getByName("debug")
            } else if (projectLumenReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
