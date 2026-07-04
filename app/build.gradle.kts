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

fun projectLumenBuildConfigString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

fun projectLumenBooleanFlag(value: String?): Boolean {
    return value?.trim()?.lowercase() in setOf("1", "true", "yes", "on")
}

android {
    namespace = "com.projectlumen.app"
    compileSdk = 37
    ndkVersion = providers.gradleProperty("projectLumenNdkVersion").get()

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
    val projectLumenApiBaseUrl = providers.environmentVariable("PROJECT_LUMEN_API_BASE_URL")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_API_BASE_URL")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: "https://eye.chloemlla.com/api"
    val projectLumenTranslationApiBaseUrl = providers.environmentVariable("PROJECT_LUMEN_TRANSLATION_API_BASE_URL")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_TRANSLATION_API_BASE_URL")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: "https://tts.chloemlla.com"
    val projectLumenTelemetryAccessToken = providers.environmentVariable("PROJECT_LUMEN_TELEMETRY_ACCESS_TOKEN")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_TELEMETRY_ACCESS_TOKEN")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: ""
    val projectLumenApiCertificatePins = providers.environmentVariable("PROJECT_LUMEN_API_CERTIFICATE_PINS")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_API_CERTIFICATE_PINS")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: ""
    val projectLumenApiCertificatePinningEnabled = projectLumenBooleanFlag(
        providers.environmentVariable("PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED")
            .orNull
            ?: providers.gradleProperty("PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED").orNull,
    )
    val projectLumenTranslationCertificatePins = providers.environmentVariable("PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINS")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINS")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: ""
    val projectLumenTranslationCertificatePinningEnabled = projectLumenBooleanFlag(
        providers.environmentVariable("PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINNING_ENABLED")
            .orNull
            ?: providers.gradleProperty("PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINNING_ENABLED").orNull,
    )
    val projectLumenRequestSigningSecret = providers.environmentVariable("PROJECT_LUMEN_REQUEST_SIGNING_SECRET")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_REQUEST_SIGNING_SECRET")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: "project-lumen-local-request-signing-key"
    val projectLumenReleaseCertSha256 = providers.environmentVariable("PROJECT_LUMEN_RELEASE_CERT_SHA256")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_RELEASE_CERT_SHA256")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: ""
    val projectLumenOpenApiTrustedSignatureSha256 = providers.environmentVariable("PROJECT_LUMEN_OPEN_API_TRUSTED_SIGNATURE_SHA256")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PROJECT_LUMEN_OPEN_API_TRUSTED_SIGNATURE_SHA256")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: ""
    val projectLumenEffectiveApiCertificatePins =
        if (projectLumenApiCertificatePinningEnabled) projectLumenApiCertificatePins else ""
    val projectLumenEffectiveTranslationCertificatePins =
        if (projectLumenTranslationCertificatePinningEnabled) projectLumenTranslationCertificatePins else ""
    require(!projectLumenApiCertificatePinningEnabled || projectLumenEffectiveApiCertificatePins.isNotBlank()) {
        "PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED=true requires PROJECT_LUMEN_API_CERTIFICATE_PINS."
    }
    require(!projectLumenTranslationCertificatePinningEnabled || projectLumenEffectiveTranslationCertificatePins.isNotBlank()) {
        "PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINNING_ENABLED=true requires PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINS."
    }

    defaultConfig {
        buildConfigField("long", "BUILD_TIME_UTC_MILLIS", "${projectLumenBuildTimeUtcMillis}L")
        buildConfigField("String", "COMMIT_HASH", "\"$projectLumenCommitHash\"")
        buildConfigField("String", "SHORT_HASH", "\"$projectLumenShortHash\"")
        buildConfigField("String", "API_BASE_URL", "\"${projectLumenBuildConfigString(projectLumenApiBaseUrl)}\"")
        buildConfigField("String", "TRANSLATION_API_BASE_URL", "\"${projectLumenBuildConfigString(projectLumenTranslationApiBaseUrl)}\"")
        buildConfigField("String", "TELEMETRY_ACCESS_TOKEN", "\"${projectLumenBuildConfigString(projectLumenTelemetryAccessToken)}\"")
        buildConfigField("String", "API_CERTIFICATE_PINS", "\"${projectLumenBuildConfigString(projectLumenEffectiveApiCertificatePins)}\"")
        buildConfigField("String", "TRANSLATION_CERTIFICATE_PINS", "\"${projectLumenBuildConfigString(projectLumenEffectiveTranslationCertificatePins)}\"")
        buildConfigField("boolean", "APP_INTEGRITY_ENFORCEMENT_ENABLED", projectLumenReleaseCertSha256.isNotBlank().toString())
        buildConfigField("String", "OPEN_API_TRUSTED_SIGNATURE_SHA256", "\"${projectLumenBuildConfigString(projectLumenOpenApiTrustedSignatureSha256)}\"")

        applicationId = "com.projectlumen.app"
        minSdk = 26
        targetSdk = 37
        versionCode = projectLumenVersionCode
        versionName = projectLumenVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DLUMEN_REQUEST_SIGNING_SECRET=${projectLumenBuildConfigString(projectLumenRequestSigningSecret)}",
                    "-DLUMEN_RELEASE_CERT_SHA256=${projectLumenBuildConfigString(projectLumenReleaseCertSha256)}",
                    "-DLUMEN_EXPECTED_PACKAGE=${projectLumenBuildConfigString("com.projectlumen.app")}",
                )
            }
        }
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

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = providers.gradleProperty("projectLumenCmakeVersion").get()
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += "**/libc++_shared.so"
        }
    }

    lint {
        disable += "GradleDependency"
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("../design")
        }
    }

}

val validateJetBrainsMonoSubset by tasks.registering {
    val subsetFont = layout.projectDirectory.file("src/main/res/font/jetbrains_mono_lumen_subset.ttf")
    inputs.file(subsetFont)
    doLast {
        val fontFile = subsetFont.asFile
        require(fontFile.exists()) {
            "JetBrains Mono subset font is missing. Generate it with pyftsubset before building."
        }
        require(fontFile.length() <= 20_000L) {
            "JetBrains Mono subset must stay below 20 KB; current size is ${fontFile.length()} bytes."
        }
    }
}

tasks.named("preBuild") {
    dependsOn(validateJetBrainsMonoSubset)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.tencent:mmkv:2.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:face-mesh-detection:16.0.0-beta3")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
