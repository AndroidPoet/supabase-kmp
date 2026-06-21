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
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.binary.compatibility.validator)
}

// Published modules whose public API and coverage we track.
val publishedModules =
    listOf(
        "supabase-core",
        "supabase-client",
        "supabase-auth",
        "supabase-auth-admin",
        "supabase-auth-google",
        "supabase-auth-apple",
        "supabase-auth-passkey",
        "supabase-database",
        "supabase-storage",
        "supabase-realtime",
        "supabase-functions",
        "supabase-e2ee",
    )

// Validate the binary (ABI) compatibility of every published module so an
// accidental public-API break is caught in review instead of by consumers.
apiValidation {
    ignoredProjects.addAll(
        subprojects.map { it.name }.filterNot { it in publishedModules },
    )
    nonPublicMarkers.add("kotlin.PublishedApi")

    // Also track the native/JS klib ABI, not just JVM + Android.
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

// ktlint engine version shared by every Spotless format below.
val ktlintVersion = libs.versions.ktlint.get()

// ktlint reads these from .editorconfig in the IDE; pass them to Spotless's
// bundled engine too so the Gradle check matches editor behaviour exactly.
//   - @Composable functions and the @FilterDsl PostgREST operators (`in`/`is`)
//     are intentionally not lowerCamelCase.
//   - line length is owned by detekt (MaxLineLength = 140); ktlint's own
//     reporting rule is off to avoid double-reporting.
//   - the function-signature rule is off because it collapses author-wrapped
//     expression bodies onto a single line that then exceeds detekt's 140 limit.
val ktlintOverrides =
    mapOf(
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable,FilterDsl",
        "ktlint_standard_max-line-length" to "disabled",
        "ktlint_standard_function-signature" to "disabled",
    )

// Format the root build script and shared config files too, not just modules.
apply(plugin = "com.diffplug.spotless")
extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
            .editorConfigOverride(ktlintOverrides)
    }
    format("misc") {
        target("*.md", ".gitignore", "config/**/*.yml")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Auto-format + lint Kotlin via ktlint (ktlint_official style, see .editorconfig).
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint(ktlintVersion)
                .editorConfigOverride(ktlintOverrides)
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
                .editorConfigOverride(ktlintOverrides)
        }
    }

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

// Coverage gate: fail the build if aggregate line coverage regresses below the
// floor. Current is ~71%; the floor sits a little below to leave headroom for
// honest refactors without letting coverage rot. Run via `./gradlew koverVerify`.
kover {
    reports {
        verify {
            rule("Aggregate line coverage") {
                minBound(65)
            }
        }
    }
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

// One-time setup so contributors get the same gates as CI before they push.
// Points git at the version-controlled .githooks/ directory (see .githooks/README.md).
tasks.register<Exec>("installGitHooks") {
    group = "git hooks"
    description = "Enable the repo's pre-commit/pre-push hooks (sets core.hooksPath to .githooks)."
    commandLine("git", "config", "core.hooksPath", ".githooks")
}
