package io.github.androidpoet.supabase.e2e

import java.io.File

internal data class LocalSupabaseConfig(
    val apiUrl: String,
    val anonKey: String,
)

/**
 * Resolves the local Supabase connection details for E2E tests.
 *
 * Prefers the `SUPABASE_E2E_URL` / `SUPABASE_E2E_ANON_KEY` environment variables
 * (set by CI); otherwise shells out to `supabase status -o env` and requires a
 * locally running stack (`supabase start`).
 */
internal fun localSupabaseConfig(): LocalSupabaseConfig {
    val envUrl = System.getenv("SUPABASE_E2E_URL")
    val envAnonKey = System.getenv("SUPABASE_E2E_ANON_KEY")
    if (!envUrl.isNullOrBlank() && !envAnonKey.isNullOrBlank()) {
        return LocalSupabaseConfig(apiUrl = envUrl, anonKey = envAnonKey)
    }

    val status =
        ProcessBuilder("supabase", "status", "-o", "env")
            .directory(File(System.getProperty("user.dir")))
            .redirectErrorStream(true)
            .start()
    val output = status.inputStream.bufferedReader().readText()
    val exitCode = status.waitFor()
    check(exitCode == 0) {
        "Unable to read local Supabase status. Run `supabase start` first.\n$output"
    }
    val values =
        output
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1].trim('"') else null
            }.toMap()
    val apiUrl =
        values["API_URL"]
            ?: values["SUPABASE_URL"]
            ?: error("Supabase status did not include API_URL.\n$output")
    val anonKey =
        values["ANON_KEY"]
            ?: values["SUPABASE_ANON_KEY"]
            ?: error("Supabase status did not include ANON_KEY.\n$output")
    return LocalSupabaseConfig(apiUrl = apiUrl, anonKey = anonKey)
}
