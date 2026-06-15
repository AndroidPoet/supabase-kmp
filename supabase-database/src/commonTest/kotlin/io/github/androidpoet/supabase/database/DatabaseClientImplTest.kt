package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseClientImplTest {
    @Test
    fun test_select_headReturnsFailureWhenRequestFails() =
        runTest {
            val client = FakeSupabaseClient(getResult = SupabaseResult.Failure(SupabaseError("boom")))
            val sut = DatabaseClientImpl(client)

            val result =
                runSuspend {
                    sut.select(table = "messages", columns = "*", head = true, count = null) {}
                }

            assertTrue(result is SupabaseResult.Failure)
        }

    @Test
    fun test_insert_onConflictIsUrlEncodedInEndpoint() =
        runTest {
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
    fun test_insert_columnsAreEncodedInEndpoint() =
        runTest {
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
    fun test_select_singleUsesObjectAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(table = "messages", single = true) {}
            }

            assertEquals("application/vnd.pgrst.object+json", client.lastGetHeaders["Accept"])
        }

    @Test
    fun test_select_customHeadersAreMergedAndCoreHeadersWin() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(
                    table = "messages",
                    schema = "private",
                    headers =
                        mapOf(
                            "X-Trace-Id" to "trace-1",
                            "Accept" to "text/plain",
                            "Accept-Profile" to "wrong",
                        ),
                ) {}
            }

            assertEquals("trace-1", client.lastGetHeaders["X-Trace-Id"])
            assertEquals("application/json", client.lastGetHeaders["Accept"])
            assertEquals("private", client.lastGetHeaders["Accept-Profile"])
        }

    @Test
    fun test_select_csvUsesTextCsvAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(table = "messages", csv = true) {}
            }

            assertEquals("text/csv", client.lastGetHeaders["Accept"])
        }

    @Test
    fun test_select_stripNullsUsesStrippedArrayAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(table = "messages", stripNulls = true) {}
            }

            assertEquals("application/vnd.pgrst.array+json;nulls=stripped", client.lastGetHeaders["Accept"])
        }

    @Test
    fun test_select_explainUsesPlanAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(
                    table = "messages",
                    explain = ExplainOptions(analyze = true, verbose = true, format = ExplainFormat.JSON),
                ) {}
            }

            assertEquals(
                """application/vnd.pgrst.plan+json; for="application/json"; options=analyze|verbose;""",
                client.lastGetHeaders["Accept"],
            )
        }

    @Test
    fun test_select_stripNullsWithCsvThrows() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            assertFailsWith<IllegalArgumentException> {
                runSuspend {
                    sut.select(table = "messages", csv = true, stripNulls = true) {}
                }
            }
        }

    @Test
    fun test_select_retryEnabledByDefault() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(table = "messages") {}
            }

            assertEquals("true", client.lastGetHeaders["X-Supabase-Kmp-Retry"])
        }

    @Test
    fun test_select_retryFalseDisablesRetry() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(table = "messages", retry = false) {}
            }

            assertEquals("false", client.lastGetHeaders["X-Supabase-Kmp-Retry"])
        }

    @Test
    fun test_insert_upsertIgnoreDuplicatesSetsPreferResolution() =
        runTest {
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
    fun test_insert_defaultToNullFalse_setsMissingDefaultPreferDirective() =
        runTest {
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
    fun test_insert_rollbackSetsPreferTxRollback() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.insert(
                    table = "messages",
                    body = "{}",
                    rollback = true,
                )
            }

            assertTrue(client.lastPostHeaders["Prefer"]?.contains("tx=rollback") == true)
        }

    @Test
    fun test_update_maxAffectedSetsStrictHandlingPreferDirectives() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.update(
                    table = "messages",
                    body = "{}",
                    maxAffected = 1,
                ) {}
            }

            val prefer = client.lastPatchHeaders["Prefer"].orEmpty()
            assertTrue(prefer.contains("handling=strict"))
            assertTrue(prefer.contains("max-affected=1"))
        }

    @Test
    fun test_update_maxAffectedZeroThrows() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            assertFailsWith<IllegalArgumentException> {
                runSuspend {
                    sut.update(
                        table = "messages",
                        body = "{}",
                        maxAffected = 0,
                    ) {}
                }
            }
        }

    @Test
    fun test_delete_stripNullsAndExplainUsesPlanForStrippedAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.delete(
                    table = "messages",
                    stripNulls = true,
                    explain = ExplainOptions(buffers = true),
                ) {}
            }

            assertEquals(
                """application/vnd.pgrst.plan+text; for="application/vnd.pgrst.array+json;nulls=stripped"; options=buffers;""",
                client.lastDeleteHeaders["Accept"],
            )
        }

    @Test
    fun test_select_withSchema_setsAcceptProfileHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.select(table = "messages", schema = "private") {}
            }

            assertEquals("private", client.lastGetHeaders["Accept-Profile"])
        }

    @Test
    fun test_update_withSchema_setsContentProfileHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.update(table = "messages", schema = "analytics", body = "{}") {}
            }

            assertEquals("analytics", client.lastPatchHeaders["Content-Profile"])
        }

    @Test
    fun test_rpc_withSchema_setsContentProfileHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.rpc(function = "my_fn", schema = "rpc_schema", params = "{}")
            }

            assertEquals("rpc_schema", client.lastPostHeaders["Content-Profile"])
        }

    @Test
    fun test_update_invalidTableThrows() =
        runTest {
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
    fun test_rpcGet_withQueryParams_buildsEncodedEndpointAndAcceptHeader() =
        runTest {
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
    fun test_rpc_customHeadersAreMergedWithJsonHeaders() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.rpc(
                    function = "archive_messages",
                    params = "{}",
                    headers = mapOf("X-Trace-Id" to "trace-rpc", "Content-Type" to "text/plain"),
                )
            }

            assertEquals("trace-rpc", client.lastPostHeaders["X-Trace-Id"])
            assertEquals("application/json", client.lastPostHeaders["Content-Type"])
            assertEquals("application/json", client.lastPostHeaders["Accept"])
        }

    @Test
    fun test_rpcGet_headReturnsFailureWhenRequestFails() =
        runTest {
            val client = FakeSupabaseClient(getResult = SupabaseResult.Failure(SupabaseError("boom")))
            val sut = DatabaseClientImpl(client)

            val result =
                runSuspend {
                    sut.rpcGet(function = "get_messages", head = true)
                }

            assertTrue(result is SupabaseResult.Failure)
        }

    @Test
    fun test_rpc_singleUsesObjectAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.rpc(function = "get_one", single = true)
            }

            assertEquals("application/vnd.pgrst.object+json", client.lastPostHeaders["Accept"])
        }

    @Test
    fun test_rpc_maxAffectedAndRollbackSetPreferDirectives() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.rpc(function = "archive_messages", rollback = true, maxAffected = 2)
            }

            val prefer = client.lastPostHeaders["Prefer"].orEmpty()
            assertTrue(prefer.contains("tx=rollback"))
            assertTrue(prefer.contains("handling=strict"))
            assertTrue(prefer.contains("max-affected=2"))
        }

    @Test
    fun test_rpcGet_csvUsesTextCsvAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.rpcGet(function = "get_many", csv = true)
            }

            assertEquals("text/csv", client.lastGetHeaders["Accept"])
        }

    @Test
    fun test_rpcGet_retryEnabledByDefault() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.rpcGet(function = "get_many")
            }

            assertEquals("true", client.lastGetHeaders["X-Supabase-Kmp-Retry"])
        }

    @Test
    fun test_rpcGet_stripNullsSingleUsesStrippedObjectAcceptHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = DatabaseClientImpl(client)

            runSuspend {
                sut.rpcGet(function = "get_one", single = true, stripNulls = true)
            }

            assertEquals("application/vnd.pgrst.object+json;nulls=stripped", client.lastGetHeaders["Accept"])
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
    var lastDeleteHeaders: Map<String, String> = emptyMap()

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
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastDeleteHeaders = headers
        return SupabaseResult.Success("{}")
    }

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

private suspend fun <T> runSuspend(block: suspend () -> T): T = block()
