plugins {
    id("org.jetbrains.kotlinx.kover")
    alias(libs.plugins.android.library)
}

android {
    namespace = "pe.lee.core.datastore"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
