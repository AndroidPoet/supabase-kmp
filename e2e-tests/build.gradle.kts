plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(project(":supabase-client"))
    testImplementation(project(":supabase-core"))
    testImplementation(project(":supabase-database"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    enabled = false
}

tasks.register<Test>("e2eTest") {
    group = "verification"
    description = "Runs E2E tests against a running local Supabase stack."
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    workingDir = rootProject.projectDir
    outputs.upToDateWhen { false }
}
