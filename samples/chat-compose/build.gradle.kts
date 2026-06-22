import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

android {
    namespace = "io.github.androidpoet.supabase.sample.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.androidpoet.supabase.sample.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?) ?: ""
        val supabaseAnonKey = (project.findProperty("SUPABASE_ANON_KEY") as String?) ?: ""
        val supabaseStorageBucket = (project.findProperty("SUPABASE_STORAGE_BUCKET") as String?) ?: "public"
        val supabaseFunctionName = (project.findProperty("SUPABASE_FUNCTION_NAME") as String?) ?: "hello-world"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_STORAGE_BUCKET", "\"$supabaseStorageBucket\"")
        buildConfigField("String", "SUPABASE_FUNCTION_NAME", "\"$supabaseFunctionName\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// --- Supabase model auto-sync -------------------------------------------------------------
// Regenerate @Serializable models from the LIVE schema into build/generated/supabase before
// every build, so they always track the database. Output lives under build/ (gitignored) and is
// never hand-edited — the SQLDelight "generated code is build output" model. Skips gracefully when
// SUPABASE_URL/SUPABASE_KEY are unset, so the sample still configures offline / without an instance.
//
// (A standalone consumer would instead apply the published plugin with `autoSync = true`; this
// sample drives the codegen CLI directly because a project can't apply a sibling module's plugin
// within the same Gradle build.)
// Resolve url/key via providers at configuration time (tracked as config-cache inputs) so we
// avoid storing script-capturing lambdas on the task, which the configuration cache rejects.
val supabaseGeneratedDir =
    layout.buildDirectory
        .dir("generated/supabase")
        .get()
        .asFile
val supabaseUrlForGen =
    providers
        .gradleProperty("SUPABASE_URL")
        .orElse(providers.environmentVariable("SUPABASE_URL"))
        .orElse("")
        .get()
val supabaseKeyForGen =
    providers
        .gradleProperty("SUPABASE_KEY")
        .orElse(providers.environmentVariable("SUPABASE_KEY"))
        .orElse("")
        .get()
val supabaseCodegenConfigured = supabaseUrlForGen.isNotBlank() && supabaseKeyForGen.isNotBlank()

val codegenClasspath: Configuration by configurations.creating
dependencies { codegenClasspath(project(":supabase-codegen")) }

val generateSupabaseModels by tasks.registering(JavaExec::class) {
    group = "supabase"
    description = "Auto-sync @Serializable models from the live Supabase schema"
    classpath = codegenClasspath
    mainClass.set("io.github.androidpoet.supabase.codegen.MainKt")
    args(
        "--url",
        supabaseUrlForGen,
        "--key",
        supabaseKeyForGen,
        "--package",
        "io.github.androidpoet.supabase.sample.chat.generated",
        "--out",
        supabaseGeneratedDir.path,
    )
    // No declared outputs → JavaExec always runs (the remote schema can change with no local
    // change). Disabled (not failed) when unconfigured so the sample still builds offline.
    enabled = supabaseCodegenConfigured
}

if (!supabaseCodegenConfigured) {
    logger.lifecycle("supabase codegen: SUPABASE_URL/SUPABASE_KEY not set — model auto-sync disabled for this build.")
}

// Compile the generated models, and regenerate them first on every Kotlin compile.
android.sourceSets
    .getByName("main")
    .java
    .srcDir(supabaseGeneratedDir)
tasks
    .matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn(generateSupabaseModels) }

dependencies {
    implementation(project(":supabase-client"))
    implementation(project(":supabase-auth"))
    implementation(project(":supabase-database"))
    implementation(project(":supabase-storage"))
    implementation(project(":supabase-realtime"))
    implementation(project(":supabase-functions"))
    implementation(project(":supabase-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.google.material)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
}
