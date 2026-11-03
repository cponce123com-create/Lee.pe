plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "pe.lee.data"
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
    api(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:domain"))
    implementation(libs.room3.runtime)
}
