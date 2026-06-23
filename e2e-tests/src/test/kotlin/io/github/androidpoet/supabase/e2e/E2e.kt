package io.github.androidpoet.supabase.e2e

import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.client.SupabaseClient
import kotlin.random.Random

/**
 * Connection details for the hosted E2E project, read from the environment so no
 * keys ever live in source. [serviceKey] is optional and only present for tests
 * that need admin/storage privileges.
 */
internal data class HostedConfig(
    val url: String,
    val anonKey: String,
    val serviceKey: String?,
)

/**
 * Hosted-project E2E configuration and SAFETY controls.
 *
 * These tests run against a REAL Supabase project, so every safeguard here exists
 * to make three properties structural rather than a matter of trust:
 *
 *  1. **Only synthetic data is ever written.** All payloads come from [FakeData],
 *     which generates values from literals in source — nothing is read from disk,
 *     the network, or any real data source.
 *  2. **Only self-created artifacts are touched.** Every bucket / object path /
 *     row / email is namespaced under [runId] (which always starts with `e2e-`).
 *     The suite never reads, lists, or deletes pre-existing data, and never issues
 *     an unscoped delete or update.
 *  3. **Everything is cleaned up.** Each test removes the artifacts it created.
 */
internal object E2e {
    /**
     * Unique, throwaway prefix for every artifact a single run creates. Begins
     * with `e2e-` so any leftover is always identifiable as test data, and carries
     * a random suffix so repeated or concurrent runs never collide.
     */
    val runId: String =
        "e2e-" + System.currentTimeMillis().toString(36) + "-" +
            Random.nextInt(0, 1_000_000).toString(36)

    /** Namespaces [base] under [runId] so a generated name can never collide with real data. */
    fun artifact(base: String): String = "$runId-$base"

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    /**
     * Resolves the hosted configuration from the environment, failing loudly with
     * setup instructions if it is missing. Also asserts the [runId] safety
     * invariant before any test touches the network.
     */
    fun config(): HostedConfig =
        configOrNull()
            ?: error(
                "E2E not configured. Export SUPABASE_E2E_URL and SUPABASE_E2E_ANON_KEY " +
                    "(and SUPABASE_E2E_SERVICE_KEY for storage/admin) before running :e2e-tests:e2eTest.",
            )

    /**
     * Like [config] but returns null instead of throwing when the hosted project
     * isn't configured (e.g. in CI, which holds no hosted credentials). Lets
     * config-dependent tests skip cleanly rather than fail.
     */
    fun configOrNull(): HostedConfig? {
        val url = env("SUPABASE_E2E_URL")
        val anon = env("SUPABASE_E2E_ANON_KEY")
        if (url.isNullOrBlank() || anon.isNullOrBlank()) return null
        check(runId.startsWith("e2e-")) { "Safety invariant violated: runId must start with 'e2e-'." }
        return HostedConfig(url = url, anonKey = anon, serviceKey = env("SUPABASE_E2E_SERVICE_KEY"))
    }

    /** The service-role key, or a clear failure telling the caller how to provide it. */
    fun requireServiceKey(config: HostedConfig): String =
        config.serviceKey
            ?: error(
                "This test needs the service-role key. Export it without it entering the chat: " +
                    "`! export SUPABASE_E2E_SERVICE_KEY=...`",
            )

    /** A client authenticated with the public anon key (subject to RLS). */
    fun anonClient(config: HostedConfig): SupabaseClient =
        Supabase.create(projectUrl = config.url, apiKey = config.anonKey)

    /** A client authenticated with the service-role key (admin/storage; bypasses RLS). */
    fun serviceClient(config: HostedConfig): SupabaseClient =
        Supabase.create(projectUrl = config.url, apiKey = requireServiceKey(config))
}
