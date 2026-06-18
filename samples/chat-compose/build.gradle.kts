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
    namespace = "io.github.androidpoet.supabase.sample.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.androidpoet.supabase.sample.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?) ?: ""
        val supabaseAnonKey = (project.findProperty("SUPABASE_ANON_KEY") as String?) ?: ""
        val supabaseStorageBucket = (project.findProperty("SUPABASE_STORAGE_BUCKET") as String?) ?: "public"
        val supabaseFunctionName = (project.findProperty("SUPABASE_FUNCTION_NAME") as String?) ?: "hello-world"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_STORAGE_BUCKET", "\"$supabaseStorageBucket\"")
        buildConfigField("String", "SUPABASE_FUNCTION_NAME", "\"$supabaseFunctionName\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":supabase-client"))
    implementation(project(":supabase-auth"))
    implementation(project(":supabase-database"))
    implementation(project(":supabase-storage"))
    implementation(project(":supabase-realtime"))
    implementation(project(":supabase-functions"))
    implementation(project(":supabase-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.google.material)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
}
