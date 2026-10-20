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
        // TDLib Android repackaging (com.github.tdlibx:td) is published through JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "arc player"
include(":app")
