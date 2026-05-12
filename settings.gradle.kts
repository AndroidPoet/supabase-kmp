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
include(":supabase-database")
include(":supabase-storage")
include(":supabase-realtime")
include(":supabase-functions")
include(":samples:chat-compose")
