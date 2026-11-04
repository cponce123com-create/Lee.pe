plugins {
    id("org.jetbrains.kotlinx.kover")
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "pe.lee.feature.library"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(project(":core:domain"))
    implementation(project(":core:ui"))
    api(project(":data"))
    implementation(project(":feature:notes"))
    implementation(project(":library:readium"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}

// Use the public variant API: the legacy sourceSets accessor crashes under AGP 9.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("../../core/database/schemas")
    }
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
