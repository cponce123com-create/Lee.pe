import dev.detekt.gradle.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.dependency.check)
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

// Apply the same generated-code exclusions to the aggregate denominator.
// Production UI and repositories remain included.
kover {
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*.R", "*.R\$*", "*_Impl", "*_Impl\$*")
            }
        }
    }
}

tasks.register("koverHtmlReportAll") {
    group = "verification"
    description = "Generates aggregate HTML coverage for the project's JVM unit tests."
    dependsOn("koverHtmlReport")
}

// Analyze source without the Android type-resolution integration. This avoids
// depending on deprecated AGP variant APIs and includes every source module.
detekt {
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(subprojects.map { it.file("src") })
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
}
tasks.withType<Detekt>().configureEach {
    include("**/*.kt")
    exclude("**/build/**")
    reports {
        html.required.set(true)
        sarif.required.set(true)
        checkstyle.required.set(true)
    }
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<KtlintExtension> {
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        reporters {
            reporter(ReporterType.CHECKSTYLE)
            reporter(ReporterType.SARIF)
        }
        filter { exclude("**/build/**") }
    }
}

tasks.register("koverXmlReportAll") {
    group = "verification"
    dependsOn(
        "koverXmlReport",
        ":core:domain:koverXmlReport",
        ":data:koverXmlReportDebug",
        ":app:koverXmlReportDebug",
        ":feature:library:koverXmlReportDebug",
        ":feature:notes:koverXmlReportDebug",
        ":feature:sharecard:koverXmlReportDebug",
    )
}
tasks.register("coverageGoals") {
    group = "verification"
    description = "Checks requested per-module line coverage targets; does not hide failures."
    dependsOn(
        ":core:domain:koverVerify",
        ":data:koverVerifyDebug",
        ":app:koverVerifyDebug",
        ":feature:library:koverVerifyDebug",
        ":feature:notes:koverVerifyDebug",
        ":feature:sharecard:koverVerifyDebug",
    )
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON", "SARIF")
    // Scan the dependency graphs shipped in the app and libraries. AGP's
    // androidJdkImage is an internal transform configuration, not an app dependency.
    scanConfigurations = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    // Absence is different from an explicitly empty (invalid) NVD credential.
    providers.environmentVariable("NVD_API_KEY").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
        nvd.apiKey = it
    }
    // No credentials are required for the local JVM/Android dependency graph.
    analyzers.assemblyEnabled = false
}
