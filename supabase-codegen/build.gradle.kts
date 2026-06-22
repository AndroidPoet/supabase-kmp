import io.github.androidpoet.supabase.Configuration

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.publish)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

application {
    // CLI entry point: fetch a project's schema and generate Kotlin models.
    mainClass.set("io.github.androidpoet.supabase.codegen.MainKt")
}

// Published so the Gradle plugin (which depends on this module) resolves its
// transitive coordinate from Maven Central. This is a JVM build-time tool, not a
// KMP artifact — only the code it *generates* is multiplatform-common.
mavenPublishing {
    coordinates(Configuration.GROUP, "supabase-codegen", Configuration.VERSION)
}
