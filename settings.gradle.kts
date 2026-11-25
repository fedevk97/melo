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
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.racra")
                includeGroup("com.github.cosmictaserdev-creator.Jetpack_SF_Symbols")
            }
        }
    }
}

rootProject.name = "Melo"
include(":app")
