pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
    }
}

rootProject.name = "Project-Lumen"
include(":app")
include(":lumen-crash-core")
include(":lumen-crash")
include(":lumen-crash-sample")
include(":baselineprofile")
