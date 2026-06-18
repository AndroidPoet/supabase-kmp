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
    namespace = "io.github.androidpoet.passkeys.sample"
    compileSdk = 35

    defaultConfig {
        // Must match androidpoet.dev assetlinks.json (package + signing SHA-256).
        applicationId = "io.github.androidpoet.passkeys.sample"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?) ?: ""
        val supabaseAnonKey = (project.findProperty("SUPABASE_ANON_KEY") as String?) ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    // Sign with the dedicated passkeys-test key whose SHA-256 is published in
    // androidpoet.dev/.well-known/assetlinks.json, so Credential Manager validates.
    signingConfigs {
        create("passkeys") {
            storeFile = file("passkeys-test.keystore")
            storePassword = "passkeystest"
            keyAlias = "passkeys-test"
            keyPassword = "passkeystest"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("passkeys") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":supabase-client"))
    implementation(project(":supabase-auth"))
    implementation(project(":supabase-auth-passkey")) // brings PasskeysKmpAuthenticator + passkeys-kmp
    implementation(project(":supabase-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
