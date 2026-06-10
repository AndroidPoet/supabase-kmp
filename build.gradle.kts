plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.binary.compatibility.validator)
}

// Published modules whose public API and coverage we track.
val publishedModules = listOf(
    "supabase-core",
    "supabase-client",
    "supabase-auth",
    "supabase-auth-admin",
    "supabase-database",
    "supabase-storage",
    "supabase-realtime",
    "supabase-functions",
)

// Validate the binary (ABI) compatibility of every published module so an
// accidental public-API break is caught in review instead of by consumers.
apiValidation {
    ignoredProjects.addAll(
        subprojects.map { it.name }.filterNot { it in publishedModules },
    )
    nonPublicMarkers.add("kotlin.PublishedApi")
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        parallel = true
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        // Analyze every Kotlin source set, not just JVM main.
        source.setFrom(
            files(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
                "src/jvmMain/kotlin",
                "src/androidMain/kotlin",
                "src/appleMain/kotlin",
                "src/jvmTest/kotlin",
            ).filter { it.exists() },
        )
    }

    // Generate API reference docs from KDoc for every published module.
    if (name in publishedModules) {
        apply(plugin = "org.jetbrains.dokka")
    }
}

// Aggregate coverage across all published modules.
dependencies {
    publishedModules.forEach { kover(project(":$it")) }
}

val supabaseStart by tasks.registering(Exec::class) {
    group = "verification"
    description = "Starts the local Supabase Docker stack."
    commandLine(
        "supabase",
        "start",
        "--exclude",
        listOf(
            "realtime",
            "storage-api",
            "imgproxy",
            "mailpit",
            "postgres-meta",
            "studio",
            "edge-runtime",
            "logflare",
            "vector",
            "supavisor",
        ).joinToString(","),
    )
}

val supabaseAppStart by tasks.registering(Exec::class) {
    group = "verification"
    description = "Starts the local Supabase Docker stack needed by the Android sample app."
    commandLine("supabase", "start")
}

val supabaseAppDbReset by tasks.registering(Exec::class) {
    group = "verification"
    description = "Resets the local Supabase app database with migrations and seed data."
    dependsOn(supabaseAppStart)
    commandLine("supabase", "db", "reset", "--local", "--yes")
}

val supabaseDbReset by tasks.registering(Exec::class) {
    group = "verification"
    description = "Resets the local Supabase database with migrations and seed data."
    dependsOn(supabaseStart)
    commandLine("supabase", "db", "reset", "--local", "--yes")
}

val supabaseStop by tasks.registering(Exec::class) {
    group = "verification"
    description = "Stops the local Supabase Docker stack."
    commandLine("supabase", "stop", "--no-backup")
}

tasks.register("supabaseE2eTest") {
    group = "verification"
    description = "Runs JVM E2E tests against the local Supabase Docker stack."
    dependsOn(supabaseDbReset)
    dependsOn(":e2e-tests:e2eTest")
    finalizedBy(supabaseStop)
}

gradle.projectsEvaluated {
    project(":e2e-tests").tasks.named("e2eTest") {
        mustRunAfter(supabaseDbReset)
    }
}
