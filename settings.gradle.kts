pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://codeberg.org/api/packages/cefrium/maven")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://codeberg.org/api/packages/cefrium/maven")
    }
}
rootProject.name = "PLAYTV"
include(":app", ":app-chromium")
