plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":app"))
    kover(project(":core:database"))
    kover(project(":core:datastore"))
    kover(project(":core:domain"))
    kover(project(":core:ui"))
    kover(project(":data"))
    kover(project(":feature:library"))
    kover(project(":feature:notes"))
    kover(project(":feature:sharecard"))
    kover(project(":library:readium"))
}

tasks.register("koverHtmlReportAll") {
    group = "verification"
    description = "Generates aggregate HTML coverage for the project's JVM unit tests."
    dependsOn("koverHtmlReport")
}
