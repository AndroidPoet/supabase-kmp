import io.github.androidpoet.supabase.Configuration

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
}

kotlin { jvmToolchain(17) }

dependencies {
    // Reuses the generator + schema fetcher from the codegen core; the plugin is just a
    // thin Gradle-task wrapper around them.
    implementation(project(":supabase-codegen"))

    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("supabaseCodegen") {
            id = "io.github.androidpoet.supabase.codegen"
            implementationClass = "io.github.androidpoet.supabase.codegen.gradle.SupabaseCodegenPlugin"
            displayName = "Supabase Codegen"
            description = "Generates Kotlin @Serializable models from your Supabase schema"
        }
    }
}

// With java-gradle-plugin applied, vanniktech also publishes the plugin *marker*
// artifact (io.github.androidpoet.supabase.codegen:…gradle.plugin), so consumers can
// apply it via `plugins { id("io.github.androidpoet.supabase.codegen") version "…" }`
// after adding mavenCentral() to pluginManagement.repositories.
mavenPublishing {
    coordinates(Configuration.GROUP, "supabase-codegen-gradle", Configuration.VERSION)
}
