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

android {
    namespace = "com.projectlumen.app"
    compileSdk = 36

    defaultConfig {
        val gitShaShort = providers.exec {
            commandLine("git", "rev-parse", "--short=8", "HEAD")
        }.standardOutput.asText.get().trim().ifBlank { "unknown" }
        buildConfigField("String", "GIT_SHA_SHORT", "\"$gitShaShort\"")

        applicationId = "com.projectlumen.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
