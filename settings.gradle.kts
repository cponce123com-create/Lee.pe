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
    }
}

rootProject.name = "Lee.pe"
include(":app", ":core:database", ":core:datastore", ":core:domain", ":core:ui", ":data", ":feature:library", ":feature:notes", ":feature:sharecard", ":library:readium")
