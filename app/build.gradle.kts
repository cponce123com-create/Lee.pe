plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "pe.lee.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "pe.lee.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":data"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:library"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.navigator.media.tts)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
