plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

val lumenCrashSdkVersion: String =
    (findProperty("lumenCrashSdkVersion") as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: run {
            val versionFile = rootProject.file("lumen-crash/sdk.version")
            if (versionFile.isFile) {
                versionFile.readText(Charsets.UTF_8)
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() }
                    ?: "0.1.0"
            } else {
                "0.1.0"
            }
        }

group = "com.chloemlla.lumen"
version = lumenCrashSdkVersion

android {
    namespace = "com.chloemlla.lumen.crash"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-extended")
    api("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")

    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.chloemlla.lumen"
            artifactId = "lumen-crash"
            version = lumenCrashSdkVersion

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Lumen Crash SDK")
                description.set(
                    "Reusable Android crash collection and adaptive Compose crash report UI extracted from Project Lumen.",
                )
                url.set("https://github.com/Chloemlla/Project-Lumen")
                licenses {
                    license {
                        name.set("Repository license")
                        url.set("https://github.com/Chloemlla/Project-Lumen")
                    }
                }
                developers {
                    developer {
                        id.set("chloemlla")
                        name.set("ChloeMlla")
                        url.set("https://github.com/Chloemlla/")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/Chloemlla/Project-Lumen.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Chloemlla/Project-Lumen.git")
                    url.set("https://github.com/Chloemlla/Project-Lumen")
                }
            }
        }
    }

    repositories {
        maven {
            name = "LocalRelease"
            url = uri(layout.buildDirectory.dir("repo"))
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
            credentials {
                username = (findProperty("gpr.user") as String?)
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: ""
                password = (findProperty("gpr.key") as String?)
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: ""
            }
        }
    }
}
