plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.github.androidpoet.supabase.sample.desktop.MainKt")
}

dependencies {
    implementation(project(":supabase-client"))
    implementation(project(":supabase-auth"))
    implementation(project(":supabase-database"))
    implementation(project(":supabase-storage"))
    implementation(project(":supabase-realtime"))
    implementation(project(":supabase-functions"))
    implementation(project(":supabase-core"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
