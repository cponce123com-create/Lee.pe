plugins {
    id("org.jetbrains.kotlinx.kover")
    alias(libs.plugins.kotlin.jvm)
    id("java-library")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
