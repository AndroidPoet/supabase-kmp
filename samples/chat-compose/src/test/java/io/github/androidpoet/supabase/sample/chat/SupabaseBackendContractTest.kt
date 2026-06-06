package io.github.androidpoet.supabase.sample.chat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SupabaseBackendContractTest {
    private val repoRoot = findRepoRoot()

    private fun read(path: String): String = File(repoRoot, path).readText()

    private fun findRepoRoot(): File {
        var current = File(System.getProperty("user.dir") ?: error("Missing user.dir"))
        while (!File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile ?: error("Could not find repository root")
        }
        return current
    }

    @Test
    fun test_backendMigration_containsChatTablesRealtimeStorageAndRpc() {
        val sql = read("supabase/migrations/20240529_add_chat_tables.sql")

        assertContains(sql, "create table if not exists public.chat_rooms")
        assertContains(sql, "create table if not exists public.chat_messages")
        assertContains(sql, "alter table public.chat_rooms enable row level security")
        assertContains(sql, "alter table public.chat_messages enable row level security")
        assertContains(sql, "alter publication supabase_realtime add table public.chat_messages")
        assertContains(sql, "insert into storage.buckets")
        assertContains(sql, "create policy \"anon insert public sample bucket\"")
        assertContains(sql, "create or replace function public.chat_room_message_count")
        assertContains(sql, "grant execute on function public.chat_room_message_count(uuid) to anon, authenticated")
    }

    @Test
    fun test_backendMigration_allowsAnonAndAuthenticatedSampleAccess() {
        val sql = read("supabase/migrations/20240529_add_chat_tables.sql")

        assertContains(sql, "to anon")
        assertContains(sql, "to authenticated")
        assertContains(sql, "using (true)")
        assertContains(sql, "with check (true)")
        assertContains(sql, "to anon, authenticated")
    }

    @Test
    fun test_backendConfig_enablesAuthFlowsUsedBySample() {
        val config = read("supabase/config.toml")

        assertContains(config, "enable_signup = true")
        assertContains(config, "enable_anonymous_sign_ins = true")
    }

    @Test
    fun test_seed_insertsDefaultRoomsIdempotently() {
        val seed = read("supabase/seed.sql")

        assertContains(seed, "where not exists (select 1 from public.chat_rooms where name = 'general')")
        assertContains(seed, "where not exists (select 1 from public.chat_rooms where name = 'random')")
    }

    @Test
    fun test_function_returnsTypedReplyForFunctionsTab() {
        val function = read("supabase/functions/hello-world/index.ts")

        assertContains(function, "reply")
        assertContains(function, "Hello World!")
        assertTrue(function.contains("body"), "Function should echo the request body for raw invoke diagnostics.")
    }
}
