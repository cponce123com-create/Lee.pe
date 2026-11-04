plugins {
    id("org.jetbrains.kotlinx.kover")
    alias(libs.plugins.android.library)
}

android {
    namespace = "pe.lee.feature.notes"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:domain"))
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}

kover {
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*.R", "*.R\$*", "*_Impl", "*_Impl\$*")
            }
        }
        verify {
            rule { minBound(40) }
        }
    }
}
