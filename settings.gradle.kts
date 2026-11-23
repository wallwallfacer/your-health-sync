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
        // Huawei HMS Health Kit SDK
        maven("https://developer.huawei.com/repo/")
    }
}

rootProject.name = "your-health-sync"
include(":app")
