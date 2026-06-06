package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseClientExtTest {
    @Test
    fun test_selectHead_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success(""),
        )

        val result = client.selectHead(table = "items")
        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_selectMaybeSingleTyped_returnsDecodedEntity() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
        )

        val result = client.selectMaybeSingleTyped<TestItem>(table = "items")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(TestItem(id = 1), result.value)
    }

    @Test
    fun test_selectMaybeSingleTyped_returnsNullOn406() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Failure(SupabaseError(message = "no rows", code = "406")),
        )

        val result = client.selectMaybeSingleTyped<TestItem>(table = "items")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(null, result.value)
    }

    @Test
    fun test_upsertTyped_setsUpsertFlag() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
        )

        client.upsertTyped(
            table = "items",
            value = TestItem(id = 1),
        )

        assertEquals(true, client.lastInsertUpsert)
    }

    @Test
    fun test_insertUnit_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            insertResult = SupabaseResult.Success(""),
        )

        val result = client.insertUnit(
            table = "items",
            body = """{"id":10}""",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_insertUnitTyped_setsUpsertFlagAndMapsUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            insertResult = SupabaseResult.Success(""),
        )

        val result = client.insertUnitTyped(
            table = "items",
            value = TestItem(id = 14),
            upsert = true,
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
        assertEquals(true, client.lastInsertUpsert)
    }

    @Test
    fun test_deleteTyped_deserializesList() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            deleteResult = SupabaseResult.Success("""[{"id":2}]"""),
        )

        val result = client.deleteTyped<TestItem>(table = "items")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf(TestItem(id = 2)), result.value)
    }

    @Test
    fun test_updateUnit_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            updateResult = SupabaseResult.Success(""),
        )

        val result = client.updateUnit(
            table = "items",
            body = """{"name":"x"}""",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_updateUnitTyped_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            updateResult = SupabaseResult.Success(""),
        )

        val result = client.updateUnitTyped(
            table = "items",
            value = TestItem(id = 15),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_deleteUnit_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            deleteResult = SupabaseResult.Success(""),
        )

        val result = client.deleteUnit(table = "items")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_rpcGetTyped_deserializesValue() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("""{"id":3}"""),
        )

        val result = client.rpcGetTyped<TestItem>(
            function = "get_item",
            queryParams = listOf("id" to "3"),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(TestItem(id = 3), result.value)
    }

    @Test
    fun test_rpcGet_mapOverload_mapsParamsToPairs() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("""{"id":3}"""),
        )

        client.rpcGet(
            function = "get_item",
            queryParams = linkedMapOf("id" to "3", "active" to "true"),
        )

        assertEquals(listOf("id" to "3", "active" to "true"), client.lastRpcGetQueryParams)
    }

    @Test
    fun test_rpcGet_mapOverload_withOptions_mapsParamsToPairs() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success(""),
        )

        client.rpcGet(
            function = "get_item",
            queryParams = linkedMapOf("id" to "3", "active" to "true"),
            head = true,
            count = CountOption.EXACT,
        )

        assertEquals(listOf("id" to "3", "active" to "true"), client.lastRpcGetQueryParams)
    }

    @Test
    fun test_rpcGetTyped_mapOverload_deserializesValue() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("""{"id":8}"""),
        )

        val result = client.rpcGetTyped<TestItem>(
            function = "get_item",
            queryParams = mapOf("id" to "8"),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(TestItem(id = 8), result.value)
    }

    @Test
    fun test_rpcGetTyped_requestObject_mapsToQueryParams() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("""{"id":13}"""),
        )

        val result = client.rpcGetTyped<TestRequest, TestItem>(
            function = "get_item",
            params = TestRequest(id = 13),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(TestItem(id = 13), result.value)
        assertEquals(listOf("id" to "13"), client.lastRpcGetQueryParams)
    }

    @Test
    fun test_rpcGetSingleTyped_mapOverload_deserializesValue() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("""{"id":9}"""),
        )

        val result = client.rpcGetSingleTyped<TestItem>(
            function = "get_item",
            queryParams = mapOf("id" to "9"),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(TestItem(id = 9), result.value)
    }

    @Test
    fun test_rpcGetListTyped_mapOverload_deserializesList() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("""[{"id":1},{"id":2}]"""),
        )

        val result = client.rpcGetListTyped<TestItem>(
            function = "get_items",
            queryParams = mapOf("active" to "true"),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf(TestItem(id = 1), TestItem(id = 2)), result.value)
    }

    @Test
    fun test_rpcGetCsv_mapOverload_returnsRawCsv() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("id\n10"),
        )

        val result = client.rpcGetCsv(
            function = "get_csv",
            queryParams = mapOf("x" to "10"),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals("id\n10", result.value)
    }

    @Test
    fun test_rpcGetHead_mapOverload_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success(""),
        )

        val result = client.rpcGetHead(
            function = "get_count",
            queryParams = mapOf("only" to "count"),
            count = CountOption.EXACT,
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_rpcListTyped_deserializesList() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success("""[{"id":4},{"id":5}]"""),
        )

        val result = client.rpcListTyped<TestItem>(function = "get_items")
        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf(TestItem(id = 4), TestItem(id = 5)), result.value)
    }

    @Test
    fun test_rpcTyped_requestObject_isSerializedToParams() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success("""{"id":11}"""),
        )

        val result = client.rpcTyped<TestRequest, TestItem>(
            function = "echo",
            params = TestRequest(id = 11),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(TestItem(id = 11), result.value)
        assertEquals("""{"id":11}""", client.lastRpcParams)
    }

    @Test
    fun test_rpcSingleTyped_requestObject_isSerializedToParams() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success("""{"id":12}"""),
        )

        val result = client.rpcSingleTyped<TestRequest, TestItem>(
            function = "echo_one",
            params = TestRequest(id = 12),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(TestItem(id = 12), result.value)
        assertEquals("""{"id":12}""", client.lastRpcParams)
    }

    @Test
    fun test_rpcUnit_requestObject_isSerializedToParams() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success("{}"),
        )

        val result = client.rpcUnit(
            function = "do_work",
            params = TestRequest(id = 42),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
        assertEquals("""{"id":42}""", client.lastRpcParams)
    }

    @Test
    fun test_rpcHead_requestObject_isSerializedToParams() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success(""),
        )

        val result = client.rpcHead(
            function = "get_count",
            params = TestRequest(id = 60),
            count = CountOption.EXACT,
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
        assertEquals("""{"id":60}""", client.lastRpcParams)
    }

    @Test
    fun test_rpcCsv_requestObject_isSerializedToParams() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success("id\n50"),
        )

        val result = client.rpcCsv(
            function = "get_csv",
            params = TestRequest(id = 50),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals("id\n50", result.value)
        assertEquals("""{"id":50}""", client.lastRpcParams)
    }

    @Test
    fun test_rpcGetListTyped_deserializesList() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("""[{"id":6},{"id":7}]"""),
        )

        val result = client.rpcGetListTyped<TestItem>(function = "get_items")
        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf(TestItem(id = 6), TestItem(id = 7)), result.value)
    }

    @Test
    fun test_rpcMaybeSingleTyped_returnsNullOn406() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Failure(SupabaseError(message = "no rows", code = "406")),
        )

        val result = client.rpcMaybeSingleTyped<TestItem>(function = "get_item")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(null, result.value)
    }

    @Test
    fun test_rpcGetMaybeSingleTyped_returnsNullOn406() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Failure(SupabaseError(message = "no rows", code = "406")),
        )

        val result = client.rpcGetMaybeSingleTyped<TestItem>(function = "get_item")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(null, result.value)
    }

    @Test
    fun test_rpcCsv_returnsRawCsvString() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success("id\n1"),
        )

        val result = client.rpcCsv(function = "get_csv")
        assertTrue(result is SupabaseResult.Success)
        assertEquals("id\n1", result.value)
    }

    @Test
    fun test_rpcGetCsv_returnsRawCsvString() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("id\n3"),
        )

        val result = client.rpcGetCsv(function = "get_csv")
        assertTrue(result is SupabaseResult.Success)
        assertEquals("id\n3", result.value)
    }

    @Test
    fun test_rpcUnit_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success("{}"),
        )

        val result = client.rpcUnit(function = "do_work")
        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_rpcGetUnit_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("{}"),
        )

        val result = client.rpcGetUnit(function = "do_work")
        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_rpcGetUnit_mapOverload_mapsParamsToPairs() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("{}"),
        )

        val result = client.rpcGetUnit(
            function = "do_work",
            queryParams = linkedMapOf("a" to "1", "b" to "2"),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf("a" to "1", "b" to "2"), client.lastRpcGetQueryParams)
    }

    @Test
    fun test_rpcGetUnit_requestObject_mapsToQueryParams() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success("{}"),
        )

        val result = client.rpcGetUnit(
            function = "do_work",
            params = TestRequest(id = 77),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf("id" to "77"), client.lastRpcGetQueryParams)
    }

    @Test
    fun test_rpcHead_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcResult = SupabaseResult.Success(""),
        )

        val result = client.rpcHead(function = "get_count", count = CountOption.EXACT)
        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }

    @Test
    fun test_rpcGetHead_mapsSuccessToUnit() = runTest {
        val client = FakeDatabaseClient(
            selectResult = SupabaseResult.Success("""{"id":1}"""),
            rpcGetResult = SupabaseResult.Success(""),
        )

        val result = client.rpcGetHead(function = "get_count", count = CountOption.EXACT)
        assertTrue(result is SupabaseResult.Success)
        assertEquals(Unit, result.value)
    }
}

@Serializable
private data class TestItem(val id: Int)

@Serializable
private data class TestRequest(val id: Int)

private class FakeDatabaseClient(
    private val selectResult: SupabaseResult<String>,
    private val insertResult: SupabaseResult<String> = SupabaseResult.Success("[]"),
    private val updateResult: SupabaseResult<String> = SupabaseResult.Success("[]"),
    private val deleteResult: SupabaseResult<String> = SupabaseResult.Success("[]"),
    private val rpcResult: SupabaseResult<String> = SupabaseResult.Success("{}"),
    private val rpcGetResult: SupabaseResult<String> = SupabaseResult.Success("""{"id":1}"""),
) : DatabaseClient {
    var lastInsertUpsert: Boolean? = null
    var lastRpcGetQueryParams: List<Pair<String, String>> = emptyList()
    var lastRpcParams: String? = null

    override suspend fun select(
        table: String,
        schema: String?,
        columns: String,
        head: Boolean,
        single: Boolean,
        csv: Boolean,
        count: CountOption?,
        stripNulls: Boolean,
        explain: ExplainOptions?,
        retry: Boolean,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> = selectResult

    override suspend fun insert(
        table: String,
        schema: String?,
        body: String,
        columns: List<String>?,
        upsert: Boolean,
        upsertResolution: UpsertResolution,
        defaultToNull: Boolean,
        onConflict: String?,
        returning: ReturnOption,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastInsertUpsert = upsert
        return insertResult
    }

    override suspend fun update(
        table: String,
        schema: String?,
        body: String,
        returning: ReturnOption,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        maxAffected: Int?,
        explain: ExplainOptions?,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> = updateResult

    override suspend fun delete(
        table: String,
        schema: String?,
        returning: ReturnOption,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        maxAffected: Int?,
        explain: ExplainOptions?,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> = deleteResult

    override suspend fun rpc(
        function: String,
        schema: String?,
        params: String?,
        head: Boolean,
        single: Boolean,
        csv: Boolean,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        maxAffected: Int?,
        explain: ExplainOptions?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastRpcParams = params
        return rpcResult
    }

    override suspend fun rpcGet(
        function: String,
        schema: String?,
        queryParams: List<Pair<String, String>>,
        head: Boolean,
        single: Boolean,
        csv: Boolean,
        count: CountOption?,
        stripNulls: Boolean,
        explain: ExplainOptions?,
        retry: Boolean,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastRpcGetQueryParams = queryParams
        return rpcGetResult
    }
}
