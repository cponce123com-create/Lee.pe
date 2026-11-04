plugins {
    id("org.jetbrains.kotlinx.kover")
    alias(libs.plugins.kotlin.jvm)
    id("java-library")
    alias(libs.plugins.dokka)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

kover {
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*.R", "*.R\$*", "*_Impl", "*_Impl\$*")
            }
        }
        verify {
            rule { minBound(60) }
        }
    }
}
