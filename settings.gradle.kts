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
include(
    ":app",
    ":core:database",
    ":core:datastore",
    ":core:domain",
    ":core:ui",
    ":data",
    ":feature:library",
    ":feature:notes",
    ":feature:sharecard",
    ":library:readium",
)

// Benchmarks use a dedicated, seeded app variant and are opt-in for normal builds.
if (providers.gradleProperty("enableBenchmarks").orNull == "true") include(":benchmark")
