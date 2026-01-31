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
        google()  // Required for Firebase and AndroidX libraries - needs dl.google.com access
        mavenCentral()
    }
}

rootProject.name = "StayAccountable"
include(":app")
