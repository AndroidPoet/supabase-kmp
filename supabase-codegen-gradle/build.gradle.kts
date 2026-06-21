plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
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
