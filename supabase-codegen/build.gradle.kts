plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
