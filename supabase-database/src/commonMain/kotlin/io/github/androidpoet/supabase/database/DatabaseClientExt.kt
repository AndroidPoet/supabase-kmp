package io.github.androidpoet.supabase.database
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseErrorCategory
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.category
import io.github.androidpoet.supabase.core.result.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

public suspend inline fun <reified T> DatabaseClient.selectTyped(
    table: String,
    schema: String? = null,
    columns: String = "*",
    single: Boolean = false,
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<List<T>> {
    val result = select(table = table, schema = schema, columns = columns, single = single, filters = filters)
    return if (single) result.deserialize<T>().map { listOf(it) } else result.deserialize()
}

public suspend inline fun <reified T> DatabaseClient.selectSingleTyped(
    table: String,
    schema: String? = null,
    columns: String = "*",
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<T> =
    select(table = table, schema = schema, columns = columns, single = true, filters = filters).deserialize()

public suspend inline fun <reified T> DatabaseClient.selectMaybeSingleTyped(
    table: String,
    schema: String? = null,
    columns: String = "*",
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<T?> {
    val result = select(table = table, schema = schema, columns = columns, single = true, filters = filters)
    return when (result) {
        is SupabaseResult.Success -> result.deserialize<T>().map { it as T? }
        is SupabaseResult.Failure ->
            if (result.error.code == "406" || result.error.category == SupabaseErrorCategory.NotFound) {
                SupabaseResult.Success(null)
            } else {
                result
            }
    }
}

public suspend fun DatabaseClient.selectCsv(
    table: String,
    schema: String? = null,
    columns: String = "*",
    filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<String> =
    select(table = table, schema = schema, columns = columns, csv = true, filters = filters)

public suspend fun DatabaseClient.selectHead(
    table: String,
    schema: String? = null,
    columns: String = "*",
    count: CountOption? = null,
    filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<Unit> =
    select(
        table = table,
        schema = schema,
        columns = columns,
        head = true,
        count = count,
        filters = filters,
    ).map { }

public suspend inline fun <reified T> DatabaseClient.insertTyped(
    table: String,
    schema: String? = null,
    value: T,
    columns: List<String>? = null,
    upsert: Boolean = false,
    upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
    defaultToNull: Boolean = true,
    onConflict: String? = null,
): SupabaseResult<List<T>> =
    insert(
        table = table,
        schema = schema,
        body = defaultJson.encodeToString(value),
        columns = columns,
        upsert = upsert,
        upsertResolution = upsertResolution,
        defaultToNull = defaultToNull,
        onConflict = onConflict,
    ).deserialize()

public suspend fun DatabaseClient.insertUnit(
    table: String,
    schema: String? = null,
    body: String,
    columns: List<String>? = null,
    upsert: Boolean = false,
    upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
    defaultToNull: Boolean = true,
    onConflict: String? = null,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    insert(
        table = table,
        schema = schema,
        body = body,
        columns = columns,
        upsert = upsert,
        upsertResolution = upsertResolution,
        defaultToNull = defaultToNull,
        onConflict = onConflict,
        returning = ReturnOption.MINIMAL,
        count = count,
    ).map { }

public suspend inline fun <reified T> DatabaseClient.insertUnitTyped(
    table: String,
    schema: String? = null,
    value: T,
    columns: List<String>? = null,
    upsert: Boolean = false,
    upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
    defaultToNull: Boolean = true,
    onConflict: String? = null,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    insertUnit(
        table = table,
        schema = schema,
        body = defaultJson.encodeToString(value),
        columns = columns,
        upsert = upsert,
        upsertResolution = upsertResolution,
        defaultToNull = defaultToNull,
        onConflict = onConflict,
        count = count,
    )

public suspend inline fun <reified T> DatabaseClient.insertTypedMany(
    table: String,
    schema: String? = null,
    values: List<T>,
    columns: List<String>? = null,
    upsert: Boolean = false,
    upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
    defaultToNull: Boolean = true,
    onConflict: String? = null,
): SupabaseResult<List<T>> =
    insert(
        table = table,
        schema = schema,
        body = defaultJson.encodeToString(values),
        columns = columns,
        upsert = upsert,
        upsertResolution = upsertResolution,
        defaultToNull = defaultToNull,
        onConflict = onConflict,
    ).deserialize()

public suspend inline fun <reified T> DatabaseClient.upsertTyped(
    table: String,
    schema: String? = null,
    value: T,
    columns: List<String>? = null,
    upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
    defaultToNull: Boolean = true,
    onConflict: String? = null,
): SupabaseResult<List<T>> =
    insertTyped(
        table = table,
        schema = schema,
        value = value,
        columns = columns,
        upsert = true,
        upsertResolution = upsertResolution,
        defaultToNull = defaultToNull,
        onConflict = onConflict,
    )

public suspend inline fun <reified T> DatabaseClient.upsertTypedMany(
    table: String,
    schema: String? = null,
    values: List<T>,
    columns: List<String>? = null,
    upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
    defaultToNull: Boolean = true,
    onConflict: String? = null,
): SupabaseResult<List<T>> =
    insertTypedMany(
        table = table,
        schema = schema,
        values = values,
        columns = columns,
        upsert = true,
        upsertResolution = upsertResolution,
        defaultToNull = defaultToNull,
        onConflict = onConflict,
    )

public suspend inline fun <reified T> DatabaseClient.updateTyped(
    table: String,
    schema: String? = null,
    value: T,
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<List<T>> =
    update(
        table = table,
        schema = schema,
        body = defaultJson.encodeToString(value),
        filters = filters,
    ).deserialize()

public suspend fun DatabaseClient.updateUnit(
    table: String,
    schema: String? = null,
    body: String,
    count: CountOption? = null,
    filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<Unit> =
    update(
        table = table,
        schema = schema,
        body = body,
        returning = ReturnOption.MINIMAL,
        count = count,
        filters = filters,
    ).map { }

public suspend inline fun <reified T> DatabaseClient.updateUnitTyped(
    table: String,
    schema: String? = null,
    value: T,
    count: CountOption? = null,
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<Unit> =
    updateUnit(
        table = table,
        schema = schema,
        body = defaultJson.encodeToString(value),
        count = count,
        filters = filters,
    )

public suspend inline fun <reified T> DatabaseClient.deleteTyped(
    table: String,
    schema: String? = null,
    returning: ReturnOption = ReturnOption.REPRESENTATION,
    count: CountOption? = null,
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<List<T>> =
    delete(
        table = table,
        schema = schema,
        returning = returning,
        count = count,
        filters = filters,
    ).deserialize()

public suspend fun DatabaseClient.deleteUnit(
    table: String,
    schema: String? = null,
    count: CountOption? = null,
    filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<Unit> =
    delete(
        table = table,
        schema = schema,
        returning = ReturnOption.MINIMAL,
        count = count,
        filters = filters,
    ).map { }

public suspend inline fun <reified T> DatabaseClient.rpcTyped(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<T> =
    rpc(function = function, schema = schema, params = params).deserialize()

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response> =
    rpc(
        function = function,
        schema = schema,
        params = defaultJson.encodeToString(params),
    ).deserialize()

public suspend fun DatabaseClient.rpcUnit(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<Unit> =
    rpc(function = function, schema = schema, params = params).map { }

public suspend inline fun <reified Request : Any> DatabaseClient.rpcUnit(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Unit> =
    rpcUnit(
        function = function,
        schema = schema,
        params = defaultJson.encodeToString(params),
    )

public suspend inline fun <reified T> DatabaseClient.rpcListTyped(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<List<T>> =
    rpc(function = function, schema = schema, params = params).deserialize()

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcListTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<List<Response>> =
    rpc(
        function = function,
        schema = schema,
        params = defaultJson.encodeToString(params),
    ).deserialize()

public suspend inline fun <reified T> DatabaseClient.rpcSingleTyped(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<T> =
    rpc(function = function, schema = schema, params = params, single = true).deserialize()

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcSingleTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response> =
    rpc(
        function = function,
        schema = schema,
        params = defaultJson.encodeToString(params),
        single = true,
    ).deserialize()

public suspend inline fun <reified T> DatabaseClient.rpcMaybeSingleTyped(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<T?> {
    val result = rpc(function = function, schema = schema, params = params, single = true)
    return when (result) {
        is SupabaseResult.Success -> result.deserialize<T>().map { it as T? }
        is SupabaseResult.Failure ->
            if (result.error.code == "406" || result.error.category == SupabaseErrorCategory.NotFound) {
                SupabaseResult.Success(null)
            } else {
                result
            }
    }
}

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcMaybeSingleTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response?> =
    when (
        val result =
            rpc(
                function = function,
                schema = schema,
                params = defaultJson.encodeToString(params),
                single = true,
            )
    ) {
        is SupabaseResult.Success -> result.deserialize<Response>().map { it as Response? }
        is SupabaseResult.Failure ->
            if (result.error.code == "406" || result.error.category == SupabaseErrorCategory.NotFound) {
                SupabaseResult.Success(null)
            } else {
                result
            }
    }

public suspend fun DatabaseClient.rpcCsv(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<String> =
    rpc(function = function, schema = schema, params = params, csv = true)

public suspend inline fun <reified Request : Any> DatabaseClient.rpcCsv(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<String> =
    rpcCsv(
        function = function,
        schema = schema,
        params = defaultJson.encodeToString(params),
    )

public suspend fun DatabaseClient.rpcHead(
    function: String,
    schema: String? = null,
    params: String? = null,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpc(function = function, schema = schema, params = params, head = true, count = count).map { }

public suspend inline fun <reified Request : Any> DatabaseClient.rpcHead(
    function: String,
    schema: String? = null,
    params: Request,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpcHead(
        function = function,
        schema = schema,
        params = defaultJson.encodeToString(params),
        count = count,
    )

public suspend inline fun <reified T> DatabaseClient.rpcGetTyped(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<T> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).deserialize()

public suspend fun DatabaseClient.rpcGet(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<String> =
    rpcGet(
        function = function,
        schema = schema,
        queryParams = queryParams.entries.map { it.key to it.value },
    )

public suspend inline fun <reified Request : Any> DatabaseClient.rpcGet(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<String> =
    rpcGet(function = function, schema = schema, queryParams = requestToQueryMap(params))

public suspend fun DatabaseClient.rpcGet(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
    head: Boolean = false,
    single: Boolean = false,
    csv: Boolean = false,
    count: CountOption? = null,
): SupabaseResult<String> =
    rpcGet(
        function = function,
        schema = schema,
        queryParams = queryParams.toPairList(),
        head = head,
        single = single,
        csv = csv,
        count = count,
    )

public suspend inline fun <reified T> DatabaseClient.rpcGetTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<T> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).deserialize()

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response> =
    rpcGetTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

public suspend fun DatabaseClient.rpcGetUnit(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<Unit> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).map { }

public suspend fun DatabaseClient.rpcGetUnit(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<Unit> =
    rpcGetUnit(function = function, schema = schema, queryParams = queryParams.toPairList())

public suspend inline fun <reified Request : Any> DatabaseClient.rpcGetUnit(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Unit> =
    rpcGetUnit(function = function, schema = schema, queryParams = requestToQueryMap(params))

public suspend inline fun <reified T> DatabaseClient.rpcGetListTyped(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<List<T>> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).deserialize()

public suspend inline fun <reified T> DatabaseClient.rpcGetListTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<List<T>> =
    rpcGetListTyped(function = function, schema = schema, queryParams = queryParams.toPairList())

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetListTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<List<Response>> =
    rpcGetListTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

public suspend inline fun <reified T> DatabaseClient.rpcGetSingleTyped(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<T> =
    rpcGet(function = function, schema = schema, queryParams = queryParams, single = true).deserialize()

public suspend inline fun <reified T> DatabaseClient.rpcGetSingleTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<T> =
    rpcGetSingleTyped(function = function, schema = schema, queryParams = queryParams.toPairList())

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetSingleTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response> =
    rpcGetSingleTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

public suspend inline fun <reified T> DatabaseClient.rpcGetMaybeSingleTyped(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<T?> {
    val result = rpcGet(function = function, schema = schema, queryParams = queryParams, single = true)
    return when (result) {
        is SupabaseResult.Success -> result.deserialize<T>().map { it as T? }
        is SupabaseResult.Failure ->
            if (result.error.code == "406" || result.error.category == SupabaseErrorCategory.NotFound) {
                SupabaseResult.Success(null)
            } else {
                result
            }
    }
}

public suspend inline fun <reified T> DatabaseClient.rpcGetMaybeSingleTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<T?> =
    rpcGetMaybeSingleTyped(function = function, schema = schema, queryParams = queryParams.toPairList())

public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetMaybeSingleTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response?> =
    rpcGetMaybeSingleTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

public suspend fun DatabaseClient.rpcGetCsv(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<String> =
    rpcGet(function = function, schema = schema, queryParams = queryParams, csv = true)

public suspend fun DatabaseClient.rpcGetCsv(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<String> =
    rpcGetCsv(function = function, schema = schema, queryParams = queryParams.toPairList())

public suspend inline fun <reified Request : Any> DatabaseClient.rpcGetCsv(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<String> =
    rpcGetCsv(function = function, schema = schema, queryParams = requestToQueryMap(params))

public suspend fun DatabaseClient.rpcGetHead(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpcGet(function = function, schema = schema, queryParams = queryParams, head = true, count = count).map { }

public suspend fun DatabaseClient.rpcGetHead(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpcGetHead(function = function, schema = schema, queryParams = queryParams.toPairList(), count = count)

public suspend inline fun <reified Request : Any> DatabaseClient.rpcGetHead(
    function: String,
    schema: String? = null,
    params: Request,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpcGetHead(function = function, schema = schema, queryParams = requestToQueryMap(params), count = count)

@PublishedApi
internal fun Map<String, String>.toPairList(): List<Pair<String, String>> =
    entries.map { it.key to it.value }

@PublishedApi
internal inline fun <reified Request : Any> requestToQueryMap(params: Request): Map<String, String> {
    val element = defaultJson.parseToJsonElement(defaultJson.encodeToString(params))
    if (element !is JsonObject) error("rpcGet request params must serialize to a JSON object")
    return element.entries.associate { (key, value) ->
        key to jsonElementToQueryValue(value)
    }
}

@PublishedApi
internal fun jsonElementToQueryValue(value: JsonElement): String =
    when (value) {
        is JsonObject -> defaultJson.encodeToString(value)
        is kotlinx.serialization.json.JsonPrimitive -> value.content
        else -> defaultJson.encodeToString(value)
    }
