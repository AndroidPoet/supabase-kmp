plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.vanniktech.publish) apply false
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
