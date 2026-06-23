plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":supabase-sync-core"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }
        appleMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
    }
}

sqldelight {
    databases {
        create("OfflineSyncDb") {
            packageName.set("io.github.androidpoet.supabase.sync.store.db")
        }
    }
}
