package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseClientImplTest {
    @Test
    fun test_select_headReturnsFailureWhenRequestFails() {
        val client = FakeSupabaseClient(getResult = SupabaseResult.Failure(SupabaseError("boom")))
        val sut = DatabaseClientImpl(client)

        val result = runSuspend {
            sut.select(table = "messages", columns = "*", head = true, count = null) {}
        }

        assertTrue(result is SupabaseResult.Failure)
    }

    @Test
    fun test_insert_onConflictIsUrlEncodedInEndpoint() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.insert(
                table = "messages",
                body = "{}",
                upsert = true,
                onConflict = "email,name",
                returning = ReturnOption.REPRESENTATION,
                count = null,
            )
        }

        assertEquals("/rest/v1/messages?on_conflict=email%2Cname", client.lastPostEndpoint)
    }

    @Test
    fun test_insert_columnsAreEncodedInEndpoint() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.insert(
                table = "messages",
                body = "{}",
                columns = listOf("id", "name"),
            )
        }

        assertEquals("/rest/v1/messages?columns=id%2Cname", client.lastPostEndpoint)
    }

    @Test
    fun test_select_singleUsesObjectAcceptHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.select(table = "messages", single = true) {}
        }

        assertEquals("application/vnd.pgrst.object+json", client.lastGetHeaders["Accept"])
    }

    @Test
    fun test_select_csvUsesTextCsvAcceptHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.select(table = "messages", csv = true) {}
        }

        assertEquals("text/csv", client.lastGetHeaders["Accept"])
    }

    @Test
    fun test_insert_upsertIgnoreDuplicatesSetsPreferResolution() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.insert(
                table = "messages",
                body = "{}",
                upsert = true,
                upsertResolution = UpsertResolution.IGNORE_DUPLICATES,
            )
        }

        assertTrue(client.lastPostHeaders["Prefer"]?.contains("resolution=ignore-duplicates") == true)
    }

    @Test
    fun test_insert_defaultToNullFalse_setsMissingDefaultPreferDirective() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.insert(
                table = "messages",
                body = "{}",
                defaultToNull = false,
            )
        }

        assertTrue(client.lastPostHeaders["Prefer"]?.contains("missing=default") == true)
    }

    @Test
    fun test_select_withSchema_setsAcceptProfileHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.select(table = "messages", schema = "private") {}
        }

        assertEquals("private", client.lastGetHeaders["Accept-Profile"])
    }

    @Test
    fun test_update_withSchema_setsContentProfileHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.update(table = "messages", schema = "analytics", body = "{}") {}
        }

        assertEquals("analytics", client.lastPatchHeaders["Content-Profile"])
    }

    @Test
    fun test_rpc_withSchema_setsContentProfileHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.rpc(function = "my_fn", schema = "rpc_schema", params = "{}")
        }

        assertEquals("rpc_schema", client.lastPostHeaders["Content-Profile"])
    }

    @Test
    fun test_update_invalidTableThrows() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        assertFailsWith<IllegalArgumentException> {
            runSuspend {
                sut.update(
                    table = "messages/users",
                    body = "{}",
                    returning = ReturnOption.MINIMAL,
                    count = null,
                ) {}
            }
        }
    }

    @Test
    fun test_rpcGet_withQueryParams_buildsEncodedEndpointAndAcceptHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.rpcGet(
                function = "get_messages",
                queryParams = listOf("user_id" to "u 1", "limit" to "10"),
            )
        }

        assertEquals("/rest/v1/rpc/get_messages?user_id=u%201&limit=10", client.lastGetEndpoint)
        assertEquals("application/json", client.lastGetHeaders["Accept"])
    }

    @Test
    fun test_rpcGet_headReturnsFailureWhenRequestFails() {
        val client = FakeSupabaseClient(getResult = SupabaseResult.Failure(SupabaseError("boom")))
        val sut = DatabaseClientImpl(client)

        val result = runSuspend {
            sut.rpcGet(function = "get_messages", head = true)
        }

        assertTrue(result is SupabaseResult.Failure)
    }

    @Test
    fun test_rpc_singleUsesObjectAcceptHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.rpc(function = "get_one", single = true)
        }

        assertEquals("application/vnd.pgrst.object+json", client.lastPostHeaders["Accept"])
    }

    @Test
    fun test_rpcGet_csvUsesTextCsvAcceptHeader() {
        val client = FakeSupabaseClient()
        val sut = DatabaseClientImpl(client)

        runSuspend {
            sut.rpcGet(function = "get_many", csv = true)
        }

        assertEquals("text/csv", client.lastGetHeaders["Accept"])
    }
}

private class FakeSupabaseClient(
    private val getResult: SupabaseResult<String> = SupabaseResult.Success("[]"),
) : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    var lastPostEndpoint: String? = null
    var lastGetEndpoint: String? = null
    var lastGetHeaders: Map<String, String> = emptyMap()
    var lastPostHeaders: Map<String, String> = emptyMap()
    var lastPatchHeaders: Map<String, String> = emptyMap()

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastGetEndpoint = if (queryParams.isEmpty()) endpoint else "$endpoint?${queryParams.joinToString("&") { "${it.first}=${it.second}" }}"
        lastGetHeaders = headers
        return getResult
    }

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        lastPostHeaders = headers
        return SupabaseResult.Success("{}")
    }
    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPatchHeaders = headers
        return SupabaseResult.Success("{}")
    }

    override suspend fun delete(
        endpoint: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override fun close() = Unit
}

private fun <T> runSuspend(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }
