package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.models.NullsPlacement
import io.github.androidpoet.supabase.core.models.OrderDirection
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Conformance tests that lock the exact PostgREST wire format the SDK emits —
 * the `Prefer`/`Accept` headers, schema profile headers, and query string —
 * against the documented PostgREST contract. These guard against silent
 * regressions in request building that would only surface against a live server.
 *
 * References:
 *  - PostgREST Prefer header: https://postgrest.org/en/stable/references/api/preferences.html
 *  - PostgREST resource representation / count / Accept media types:
 *    https://postgrest.org/en/stable/references/api/tables_views.html
 *  - PostgREST schema selection (Accept-Profile / Content-Profile):
 *    https://postgrest.org/en/stable/references/api/schemas.html
 */
class PostgrestConformanceTest {
    // --- Prefer header ---------------------------------------------------------

    @Test
    fun test_insert_default_prefersReturnRepresentation() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).insert(table = "todos", body = "{}")
            assertEquals("return=representation", client.postHeaders["Prefer"])
        }

    @Test
    fun test_insert_upsertWithCountAndMissingAndRollback_combinesPreferDirectivesInOrder() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).insert(
                table = "todos",
                body = "{}",
                upsert = true,
                upsertResolution = UpsertResolution.MERGE_DUPLICATES,
                defaultToNull = false,
                count = CountOption.EXACT,
                rollback = true,
            )
            assertEquals(
                "return=representation, count=exact, resolution=merge-duplicates, missing=default, tx=rollback",
                client.postHeaders["Prefer"],
            )
        }

    @Test
    fun test_insert_ignoreDuplicates_usesIgnoreResolution() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).insert(
                table = "todos",
                body = "{}",
                upsert = true,
                upsertResolution = UpsertResolution.IGNORE_DUPLICATES,
            )
            assertEquals("return=representation, resolution=ignore-duplicates", client.postHeaders["Prefer"])
        }

    @Test
    fun test_update_maxAffected_addsStrictHandlingDirectives() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).update(
                table = "todos",
                body = "{}",
                maxAffected = 5,
            ) {}
            assertEquals("return=representation, handling=strict, max-affected=5", client.patchHeaders["Prefer"])
        }

    @Test
    fun test_update_minimalReturn_prefersReturnMinimal() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).update(
                table = "todos",
                body = "{}",
                returning = ReturnOption.MINIMAL,
            ) {}
            assertEquals("return=minimal", client.patchHeaders["Prefer"])
        }

    @Test
    fun test_select_countExact_prefersCount() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", count = CountOption.EXACT) {}
            assertEquals("count=exact", client.getHeaders["Prefer"])
        }

    @Test
    fun test_select_noPreferences_omitsPreferHeader() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos") {}
            assertEquals(null, client.getHeaders["Prefer"])
        }

    // --- Accept header ---------------------------------------------------------

    @Test
    fun test_select_single_usesObjectMediaType() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", single = true) {}
            assertEquals("application/vnd.pgrst.object+json", client.getHeaders["Accept"])
        }

    @Test
    fun test_select_singleStripNulls_appendsNullsStripped() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", single = true, stripNulls = true) {}
            assertEquals("application/vnd.pgrst.object+json;nulls=stripped", client.getHeaders["Accept"])
        }

    @Test
    fun test_select_arrayStripNulls_usesArrayMediaTypeWithNullsStripped() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", stripNulls = true) {}
            assertEquals("application/vnd.pgrst.array+json;nulls=stripped", client.getHeaders["Accept"])
        }

    @Test
    fun test_select_csv_usesTextCsv() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", csv = true) {}
            assertEquals("text/csv", client.getHeaders["Accept"])
        }

    @Test
    fun test_select_geojson_usesGeoJsonMediaType() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", geojson = true) {}
            assertEquals("application/geo+json", client.getHeaders["Accept"])
        }

    @Test
    fun test_select_explain_usesPlanMediaTypeWithOptions() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(
                table = "todos",
                explain = ExplainOptions(analyze = true, verbose = true, format = ExplainFormat.TEXT),
            ) {}
            assertEquals(
                "application/vnd.pgrst.plan+text; for=\"application/json\"; options=analyze|verbose;",
                client.getHeaders["Accept"],
            )
        }

    // --- Schema profile headers ------------------------------------------------

    @Test
    fun test_select_withSchema_usesAcceptProfile() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", schema = "analytics") {}
            assertEquals("analytics", client.getHeaders["Accept-Profile"])
            assertEquals(null, client.getHeaders["Content-Profile"])
        }

    @Test
    fun test_insert_withSchema_usesContentProfile() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).insert(table = "todos", schema = "analytics", body = "{}")
            assertEquals("analytics", client.postHeaders["Content-Profile"])
            assertEquals(null, client.postHeaders["Accept-Profile"])
        }

    // --- Query string ----------------------------------------------------------

    @Test
    fun test_select_emitsSelectAndFilterQueryParams() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos", columns = "id,title") {
                eq("status", "active")
                order("created_at", ascending = false)
                limit(10)
            }
            assertEquals(
                "/rest/v1/todos?select=id,title&status=eq.active&order=created_at.desc&limit=10",
                client.getEndpoint,
            )
        }

    @Test
    fun test_select_filterValueWithComma_isQuotedInQueryString() =
        runTest {
            // PostgREST treats comma as a structural separator, so a value containing
            // one must be double-quoted to keep its meaning.
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos") {
                eq("tag", "a,b")
            }
            assertEquals("""/rest/v1/todos?select=*&tag=eq."a,b"""", client.getEndpoint)
        }

    @Test
    fun test_select_matchOperator_emitsTildeFilter() =
        runTest {
            // PostgREST maps SQL `~` to the `match` query operator.
            // https://postgrest.org/en/stable/references/api/tables_views.html#operators
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos") {
                match("title", "^Buy")
            }
            assertEquals("/rest/v1/todos?select=*&title=match.^Buy", client.getEndpoint)
        }

    @Test
    fun test_select_imatchOperator_emitsCaseInsensitiveTildeFilter() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos") {
                imatch("title", "^buy")
            }
            assertEquals("/rest/v1/todos?select=*&title=imatch.^buy", client.getEndpoint)
        }

    @Test
    fun test_select_inNumberList_emitsUnquotedInFilter() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos") {
                `in`("priority", listOf(1, 2, 3))
            }
            assertEquals("/rest/v1/todos?select=*&priority=in.(1,2,3)", client.getEndpoint)
        }

    @Test
    fun test_select_containsList_emitsArrayLiteralFilter() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos") {
                contains("tags", listOf("urgent", "home"))
            }
            assertEquals("/rest/v1/todos?select=*&tags=cs.{urgent,home}", client.getEndpoint)
        }

    @Test
    fun test_select_orderEnumWithNulls_emitsDescNullsLast() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).select(table = "todos") {
                order("created_at", OrderDirection.DESCENDING, NullsPlacement.LAST)
            }
            assertEquals("/rest/v1/todos?select=*&order=created_at.desc.nullslast", client.getEndpoint)
        }

    @Test
    fun test_insert_jsonContentTypeHeaderSet() =
        runTest {
            val client = RecordingClient()
            DatabaseClientImpl(client).insert(table = "todos", body = "{}")
            assertEquals("application/json", client.postHeaders["Content-Type"])
        }
}

private class RecordingClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    var getEndpoint: String? = null
    var getHeaders: Map<String, String> = emptyMap()
    var postHeaders: Map<String, String> = emptyMap()
    var patchHeaders: Map<String, String> = emptyMap()
    var deleteHeaders: Map<String, String> = emptyMap()

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        getEndpoint =
            if (queryParams.isEmpty()) {
                endpoint
            } else {
                "$endpoint?${queryParams.joinToString("&") { "${it.first}=${it.second}" }}"
            }
        getHeaders = headers
        return SupabaseResult.Success("[]")
    }

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        postHeaders = headers
        return SupabaseResult.Success("[]")
    }

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        patchHeaders = headers
        return SupabaseResult.Success("[]")
    }

    override suspend fun delete(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        deleteHeaders = headers
        return SupabaseResult.Success("[]")
    }

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("[]")

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("[]")

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("[]")

    override suspend fun rawRequest(
        method: SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override fun close() = Unit
}
