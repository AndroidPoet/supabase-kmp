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
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()
    linuxX64()
    mingwX64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":supabase-core"))
            // The key directory and EncryptedRoom store keys/ciphertext over the
            // database and deliver live messages over realtime, so this opt-in
            // module pairs the crypto with both (a Supabase chat app uses them
            // anyway). The raw crypto API stays usable without touching either.
            api(project(":supabase-database"))
            api(project(":supabase-realtime"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.cryptography.random)
            implementation(libs.cryptography.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Client-side crypto delegates to each platform's native backend; wire one
        // cryptography-kotlin provider per target (same split as supabase-auth).
        jvmMain.dependencies { implementation(libs.cryptography.provider.jdk) }
        androidMain.dependencies { implementation(libs.cryptography.provider.jdk) }
        appleMain.dependencies { implementation(libs.cryptography.provider.apple) }
        wasmJsMain.dependencies { implementation(libs.cryptography.provider.webcrypto) }
        val linuxX64Main by getting { dependencies { implementation(libs.cryptography.provider.openssl3.prebuilt) } }
        val mingwX64Main by getting { dependencies { implementation(libs.cryptography.provider.openssl3.prebuilt) } }
    }
}

android {
    namespace = "io.github.androidpoet.supabase.e2ee"
    compileSdk = Configuration.COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "supabase-e2ee", Configuration.VERSION)
}
