pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// CRooot SDK: prefer a local composite build when the repository is checked out as a sibling.
// CI builds must check out CRooot alongside Project-Lumen:
//   git clone https://github.com/Chloemlla/CRooot.git ../CRooot
// Local development: clone CRooot as a sibling directory, or place an AAR in app/libs/.
val croootDir = rootProject.projectDir.parentFile?.resolve("CRooot")
val hasCroootSibling = croootDir?.exists() == true

if (hasCroootSibling) {
    includeBuild(croootDir!!.path) {
        dependencySubstitution {
            substitute(module("com.chloemlla.crooot:crooot-sdk"))
                .using(project(":sdk"))
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://nexus.itgsa.com:5566/repository/release/")
            credentials {
                username = "developer"
                password = "developer!@#"
            }
        }
        maven(url = "https://jitpack.io") {
            content { includeGroup("com.github.Tencent.soter") }
        }
        if (!hasCroootSibling) {
            maven {
                url = uri("https://maven.pkg.github.com/Chloemlla/CRooot")
                content { includeGroup("com.chloemlla.crooot") }
                credentials {
                    username = providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                    password = providers.gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
                }
            }
        }
    }
}

rootProject.name = "Project-Lumen"
include(":app")
include(":lumen-crash-core")
include(":lumen-crash")
include(":lumen-crash-sample")
include(":baselineprofile")