pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "supabase-kmp"

include(":supabase-core")
include(":supabase-client")
include(":supabase-auth")
include(":supabase-auth-admin")
include(":supabase-auth-google")
include(":supabase-auth-apple")
include(":supabase-auth-passkey")
include(":supabase-database")
include(":supabase-storage")
include(":supabase-realtime")
include(":supabase-functions")
include(":supabase-e2ee")
include(":supabase-codegen")
include(":supabase-codegen-gradle")
include(":supabase-sync-core")
include(":supabase-sync-sqldelight")
include(":supabase-sync")
include(":supabase-sync-paging")
include(":e2e-tests")
include(":samples:chat-compose")
include(":samples:auth-starter")
include(":samples:storage-gallery")
include(":samples:todo-crud")
include(":samples:passkey-web")

// Local-only JVM desktop demo: gitignored and not pushed to GitHub. Only
// include it when the module is present on disk so a fresh clone still builds.
if (rootDir.resolve("samples/desktop-demo/build.gradle.kts").exists()) {
    include(":samples:desktop-demo")
}
