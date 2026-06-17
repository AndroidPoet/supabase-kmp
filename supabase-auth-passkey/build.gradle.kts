@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import io.github.androidpoet.supabase.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    androidTarget { publishLibraryVariants("release") }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":supabase-auth"))
            // The SupabaseClient-level passkey helpers take a SupabaseClient
            // receiver, so it must be on the API surface — supabase-auth depends on
            // it only as `implementation`, which isn't transitive.
            api(project(":supabase-client"))
            // passkeys-kmp supplies the cross-platform PasskeyClient that
            // PasskeysKmpAuthenticator wraps; api so consumers can construct the
            // platform client (PasskeyClient is on the authenticator's surface).
            api(libs.passkeys)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.credentials)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

android {
    namespace = "io.github.androidpoet.supabase.auth.passkey"
    compileSdk = Configuration.COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "supabase-auth-passkey", Configuration.VERSION)
}
