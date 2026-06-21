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
        // For com.github.lincollincol:amplituda
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Lyrictica"
include(":app")
include(":baselineprofile")
