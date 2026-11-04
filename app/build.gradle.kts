import java.util.Properties

plugins {
    id("org.jetbrains.kotlinx.kover")
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Single source of truth for the version, shared with CI.
val versionProperties = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val appVersionCode = versionProperties.getProperty("versionCode", "1").toInt()
val appVersionName = versionProperties.getProperty("versionName", "0.1.0")

// Release signing comes from the environment; the repository never stores a
// keystore or its passwords. Without these variables the release build is
// produced unsigned (useful for CI size checks and R8 validation).
val releaseKeystore = System.getenv("LEE_PE_KEYSTORE")

android {
    namespace = "pe.lee.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "pe.lee.app"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("LEE_PE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LEE_PE_KEY_ALIAS")
                keyPassword = System.getenv("LEE_PE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    if (providers.gradleProperty("enableBenchmarks").orNull == "true") {
        buildTypes.create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
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
    implementation(project(":feature:sharecard"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
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
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

kover {
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*.R", "*.R\$*", "*_Impl", "*_Impl\$*")
            }
        }
        verify {
            rule { minBound(30) }
        }
    }
}
