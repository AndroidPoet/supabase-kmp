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
include(":supabase-database")
include(":supabase-storage")
include(":supabase-realtime")
include(":supabase-functions")
include(":e2e-tests")
include(":samples:chat-compose")
include(":samples:desktop-demo")
