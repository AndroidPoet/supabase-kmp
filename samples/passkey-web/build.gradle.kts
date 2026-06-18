@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    wasmJs {
        moduleName = "passkeyweb"
        browser {
            commonWebpackConfig {
                outputFileName = "passkeyweb.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":supabase-client"))
                implementation(project(":supabase-auth"))
                // Brings supabase-kmp's passkey helpers + the published
                // io.github.androidpoet:passkeys WasmJsPasskeyClient (transitively).
                implementation(project(":supabase-auth-passkey"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
