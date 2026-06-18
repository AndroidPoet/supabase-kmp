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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Reads rows from [table] and decodes them into a `List<T>` — the type-safe
 * wrapper over [DatabaseClient.select].
 *
 * Deserialization failures and HTTP errors both come back as
 * [SupabaseResult.Failure]; nothing is thrown. When [single] is true the request
 * expects exactly one row (`application/vnd.pgrst.object+json`) and the decoded
 * object is wrapped in a single-element list; otherwise the body is decoded as a
 * JSON array. [columns] is the PostgREST `select=` projection.
 */
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

/**
 * Selects rows and decodes them into a [PostgrestPage] carrying the total
 * [PostgrestPage.count] reported by PostgREST's `Content-Range` header. Combine
 * with `limit`/`range` filters for count-aware pagination.
 */
public suspend inline fun <reified T> DatabaseClient.selectWithCount(
    table: String,
    schema: String? = null,
    columns: String = "*",
    count: CountOption = CountOption.EXACT,
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<PostgrestPage<T>> =
    when (
        val result =
            selectRange(table = table, schema = schema, columns = columns, count = count, filters = filters)
    ) {
        is SupabaseResult.Success ->
            when (val rows = SupabaseResult.Success(result.value.first).deserialize<List<T>>()) {
                is SupabaseResult.Success ->
                    SupabaseResult.Success(
                        PostgrestPage(
                            rows = rows.value,
                            count = result.value.second.count,
                            range = result.value.second.range,
                        ),
                    )
                is SupabaseResult.Failure -> rows
            }
        is SupabaseResult.Failure -> result
    }

/**
 * Selects rows as a GeoJSON `FeatureCollection` (PostGIS), returned as the raw
 * JSON string. Requests `Accept: application/geo+json`.
 */
public suspend fun DatabaseClient.selectGeoJson(
    table: String,
    schema: String? = null,
    columns: String = "*",
    headers: Map<String, String> = emptyMap(),
    filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<String> =
    select(
        table = table,
        schema = schema,
        columns = columns,
        geojson = true,
        headers = headers,
        filters = filters,
    )

/**
 * Reads the single row matching [filters] and decodes it into [T].
 *
 * Requests `single = true`, so PostgREST returns 406 (a [SupabaseResult.Failure]
 * with [SupabaseErrorCategory.NotFound]) when zero or more than one row matches —
 * use [selectMaybeSingleTyped] if "no row" should be a success with `null`.
 */
public suspend inline fun <reified T> DatabaseClient.selectSingleTyped(
    table: String,
    schema: String? = null,
    columns: String = "*",
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<T> =
    select(table = table, schema = schema, columns = columns, single = true, filters = filters).deserialize()

/**
 * Reads at most one row matching [filters], returning `null` instead of failing
 * when none exists — the lenient variant of [selectSingleTyped].
 *
 * On a "no rows" response (HTTP 406 / [SupabaseErrorCategory.NotFound]) this maps
 * to `SupabaseResult.Success(null)`; any other failure is propagated unchanged.
 * Still fails if more than one row matches.
 */
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

/**
 * Reads rows from [table] as CSV (`Accept: text/csv`), returned as the raw
 * response string with a header row — convenient for export/download.
 */
public suspend fun DatabaseClient.selectCsv(
    table: String,
    schema: String? = null,
    columns: String = "*",
    filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<String> =
    select(table = table, schema = schema, columns = columns, csv = true, filters = filters)

/**
 * Issues a `HEAD` request to test existence or fetch a count without
 * transferring rows, discarding the (empty) body.
 *
 * Pass [count] (e.g. [CountOption.EXACT]) to have the total reported in
 * `Content-Range`; for the parsed total use [DatabaseClient.selectCount]. A
 * success means the query ran; it does not indicate whether any row matched.
 */
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

/**
 * Inserts a single [value] and returns the inserted row(s) decoded as `List<T>`
 * — the typed wrapper over [DatabaseClient.insert] with
 * [ReturnOption.REPRESENTATION].
 *
 * [value] is serialized to JSON for the request body; the echoed representation
 * is decoded back into [T] (DB defaults/triggers are reflected). Set [upsert] to
 * turn this into an upsert on [onConflict] resolved per [upsertResolution]; for
 * upsert intent specifically, [upsertTyped] reads more clearly.
 */
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

/**
 * Inserts the raw JSON [body] without fetching the result back — uses
 * [ReturnOption.MINIMAL] and discards the (empty) response.
 *
 * Use this when you do not need the inserted row, to save the round-trip payload.
 * [body] may be a single object or an array (bulk insert). Pass [count] to learn
 * how many rows were written via `Content-Range`.
 */
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

/**
 * Inserts a single [value] without fetching it back — the typed counterpart to
 * [insertUnit] that serializes [value] to JSON for you.
 */
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

/**
 * Bulk-inserts [values] in one request and returns the inserted rows as
 * `List<T>` — the multi-row form of [insertTyped].
 *
 * [values] is serialized as a JSON array; the union of all elements' keys is sent
 * as `columns=` so a field present on only some rows is still inserted (rows
 * omitting it fall back to DEFAULT/NULL) rather than being dropped from the
 * first-row inference.
 */
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

/**
 * Upserts a single [value] (insert-or-update on conflict) and returns the
 * resulting row(s) — [insertTyped] with `upsert = true` and a clearer name.
 *
 * Set [onConflict] to the unique column(s) PostgREST should match on; existing
 * rows are merged or skipped per [upsertResolution].
 */
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

/**
 * Bulk upserts [values] in one request and returns the resulting rows — the
 * multi-row form of [upsertTyped] (i.e. [insertTypedMany] with `upsert = true`).
 */
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

/**
 * Updates rows matching [filters] with the fields of [value] and returns the
 * updated rows as `List<T>` — the typed wrapper over [DatabaseClient.update].
 *
 * [value] is serialized to JSON and applied as a partial update to every matched
 * row; the echoed [ReturnOption.REPRESENTATION] body is decoded back into [T]. An
 * empty [filters] block updates the whole table, so scope it carefully.
 */
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

/**
 * Updates rows matching [filters] with the raw JSON [body] without fetching the
 * result back — [ReturnOption.MINIMAL], discarding the empty response.
 *
 * Pass [count] to learn how many rows changed via `Content-Range`. An empty
 * [filters] block updates every row.
 */
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

/**
 * Updates rows matching [filters] with [value] without fetching them back — the
 * typed counterpart to [updateUnit] that serializes [value] for you.
 */
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

/**
 * Deletes rows matching [filters] and returns the deleted rows as `List<T>` —
 * the typed wrapper over [DatabaseClient.delete] with
 * [ReturnOption.REPRESENTATION].
 *
 * Useful when you need the removed data (e.g. to undo or audit). An empty
 * [filters] block deletes the whole table, so scope it carefully; use
 * [deleteUnit] when the returned rows are not needed.
 */
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

/**
 * Deletes rows matching [filters] without fetching them back —
 * [ReturnOption.MINIMAL], discarding the empty response.
 *
 * Pass [count] to learn how many rows were removed via `Content-Range`. An empty
 * [filters] block deletes every row.
 */
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

/**
 * Calls stored procedure [function] (POST) and decodes its result into [T] — the
 * typed wrapper over [DatabaseClient.rpc] taking a raw JSON [params] string.
 */
public suspend inline fun <reified T> DatabaseClient.rpcTyped(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<T> =
    rpc(function = function, schema = schema, params = params).deserialize()

/**
 * Calls stored procedure [function] (POST) with a serializable [params] object as
 * arguments and decodes the result into [Response] — the fully typed form of
 * [rpcTyped] (arguments are serialized to a JSON object for you).
 */
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

/**
 * Calls stored procedure [function] (POST) for its side effects, discarding the
 * result — pass arguments as a raw JSON [params] string.
 */
public suspend fun DatabaseClient.rpcUnit(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<Unit> =
    rpc(function = function, schema = schema, params = params).map { }

/**
 * Calls stored procedure [function] (POST) for its side effects with a
 * serializable [params] object, discarding the result — the typed form of
 * [rpcUnit].
 */
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

/**
 * Calls stored procedure [function] (POST) returning a set, decoded into
 * `List<T>` — the list-result form of [rpcTyped] for set-returning functions.
 */
public suspend inline fun <reified T> DatabaseClient.rpcListTyped(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<List<T>> =
    rpc(function = function, schema = schema, params = params).deserialize()

/**
 * Calls set-returning stored procedure [function] (POST) with a serializable
 * [params] object, decoding the result into `List<Response>` — the typed form of
 * [rpcListTyped].
 */
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

/**
 * Calls stored procedure [function] (POST) expecting exactly one row/scalar,
 * decoded into [T].
 *
 * Requests `single = true`, so a result of zero or more than one row fails with
 * HTTP 406 / [SupabaseErrorCategory.NotFound]; use [rpcMaybeSingleTyped] to treat
 * "no row" as `null`.
 */
public suspend inline fun <reified T> DatabaseClient.rpcSingleTyped(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<T> =
    rpc(function = function, schema = schema, params = params, single = true).deserialize()

/**
 * Calls single-result stored procedure [function] (POST) with a serializable
 * [params] object, decoding into [Response] — the typed form of
 * [rpcSingleTyped]. Fails with 406 when not exactly one row is returned.
 */
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

/**
 * Calls stored procedure [function] (POST) expecting at most one row, returning
 * `null` instead of failing when none is produced — the lenient variant of
 * [rpcSingleTyped].
 *
 * A "no rows" response (HTTP 406 / [SupabaseErrorCategory.NotFound]) maps to
 * `SupabaseResult.Success(null)`; other failures propagate unchanged.
 */
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

/**
 * Calls at-most-one-row stored procedure [function] (POST) with a serializable
 * [params] object, returning `null` when none is produced — the typed form of
 * [rpcMaybeSingleTyped].
 */
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

/**
 * Calls stored procedure [function] (POST) requesting `text/csv`, returning the
 * raw CSV string.
 */
public suspend fun DatabaseClient.rpcCsv(
    function: String,
    schema: String? = null,
    params: String? = null,
): SupabaseResult<String> =
    rpc(function = function, schema = schema, params = params, csv = true)

/**
 * Calls stored procedure [function] (POST) requesting CSV, with a serializable
 * [params] object — the typed form of [rpcCsv].
 */
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

/**
 * Issues stored procedure [function] (POST) as a `HEAD` request for its
 * headers/[count] only, discarding the empty body.
 */
public suspend fun DatabaseClient.rpcHead(
    function: String,
    schema: String? = null,
    params: String? = null,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpc(function = function, schema = schema, params = params, head = true, count = count).map { }

/**
 * Issues stored procedure [function] (POST) as a `HEAD` request with a
 * serializable [params] object — the typed form of [rpcHead].
 */
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

/**
 * Calls read-only stored procedure [function] (GET) and decodes the result into
 * [T] — the typed wrapper over [DatabaseClient.rpcGet] taking ordered
 * [queryParams] argument pairs.
 */
public suspend inline fun <reified T> DatabaseClient.rpcGetTyped(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<T> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).deserialize()

/**
 * Calls read-only stored procedure [function] (GET), passing arguments as a
 * [Map] — the `Map` convenience over [DatabaseClient.rpcGet]'s pair-list form.
 */
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

/**
 * Calls read-only stored procedure [function] (GET) with a serializable [params]
 * object; its top-level fields are flattened into query arguments (object/array
 * values are re-encoded as JSON strings).
 */
public suspend inline fun <reified Request : Any> DatabaseClient.rpcGet(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<String> =
    rpcGet(function = function, schema = schema, queryParams = requestToQueryMap(params))

/**
 * Calls read-only stored procedure [function] (GET) with [Map] arguments and the
 * full set of response options ([head], [single], [csv], [count]) — the
 * `Map`-based counterpart of [DatabaseClient.rpcGet].
 */
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

/**
 * Calls read-only stored procedure [function] (GET) with [Map] arguments and
 * decodes the result into [T] — the `Map` form of [rpcGetTyped].
 */
public suspend inline fun <reified T> DatabaseClient.rpcGetTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<T> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).deserialize()

/**
 * Calls read-only stored procedure [function] (GET) with a serializable [params]
 * object and decodes the result into [Response] — the fully typed form of
 * [rpcGetTyped].
 */
public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response> =
    rpcGetTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

/**
 * Calls read-only stored procedure [function] (GET) for its result alone,
 * discarding the body — the pair-list form.
 */
public suspend fun DatabaseClient.rpcGetUnit(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<Unit> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).map { }

/**
 * Calls read-only stored procedure [function] (GET) with [Map] arguments,
 * discarding the body — the `Map` form of [rpcGetUnit].
 */
public suspend fun DatabaseClient.rpcGetUnit(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<Unit> =
    rpcGetUnit(function = function, schema = schema, queryParams = queryParams.toPairList())

/**
 * Calls read-only stored procedure [function] (GET) with a serializable [params]
 * object, discarding the body — the typed form of [rpcGetUnit].
 */
public suspend inline fun <reified Request : Any> DatabaseClient.rpcGetUnit(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Unit> =
    rpcGetUnit(function = function, schema = schema, queryParams = requestToQueryMap(params))

/**
 * Calls set-returning read-only stored procedure [function] (GET) and decodes the
 * result into `List<T>` — the GET counterpart of [rpcListTyped].
 */
public suspend inline fun <reified T> DatabaseClient.rpcGetListTyped(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<List<T>> =
    rpcGet(function = function, schema = schema, queryParams = queryParams).deserialize()

/**
 * Calls set-returning read-only stored procedure [function] (GET) with [Map]
 * arguments, decoding into `List<T>` — the `Map` form of [rpcGetListTyped].
 */
public suspend inline fun <reified T> DatabaseClient.rpcGetListTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<List<T>> =
    rpcGetListTyped(function = function, schema = schema, queryParams = queryParams.toPairList())

/**
 * Calls set-returning read-only stored procedure [function] (GET) with a
 * serializable [params] object, decoding into `List<Response>` — the typed form
 * of [rpcGetListTyped].
 */
public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetListTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<List<Response>> =
    rpcGetListTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

/**
 * Calls read-only stored procedure [function] (GET) expecting exactly one
 * row/scalar, decoded into [T] — the GET counterpart of [rpcSingleTyped]. Fails
 * with HTTP 406 / [SupabaseErrorCategory.NotFound] when not exactly one row is
 * returned.
 */
public suspend inline fun <reified T> DatabaseClient.rpcGetSingleTyped(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<T> =
    rpcGet(function = function, schema = schema, queryParams = queryParams, single = true).deserialize()

/**
 * Calls single-result read-only stored procedure [function] (GET) with [Map]
 * arguments — the `Map` form of [rpcGetSingleTyped].
 */
public suspend inline fun <reified T> DatabaseClient.rpcGetSingleTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<T> =
    rpcGetSingleTyped(function = function, schema = schema, queryParams = queryParams.toPairList())

/**
 * Calls single-result read-only stored procedure [function] (GET) with a
 * serializable [params] object — the typed form of [rpcGetSingleTyped].
 */
public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetSingleTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response> =
    rpcGetSingleTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

/**
 * Calls read-only stored procedure [function] (GET) expecting at most one row,
 * returning `null` instead of failing when none is produced — the GET, lenient
 * counterpart of [rpcSingleTyped] (HTTP 406 / [SupabaseErrorCategory.NotFound]
 * maps to `null`).
 */
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

/**
 * Calls at-most-one-row read-only stored procedure [function] (GET) with [Map]
 * arguments — the `Map` form of [rpcGetMaybeSingleTyped].
 */
public suspend inline fun <reified T> DatabaseClient.rpcGetMaybeSingleTyped(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<T?> =
    rpcGetMaybeSingleTyped(function = function, schema = schema, queryParams = queryParams.toPairList())

/**
 * Calls at-most-one-row read-only stored procedure [function] (GET) with a
 * serializable [params] object — the typed form of [rpcGetMaybeSingleTyped].
 */
public suspend inline fun <reified Request : Any, reified Response> DatabaseClient.rpcGetMaybeSingleTyped(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<Response?> =
    rpcGetMaybeSingleTyped(function = function, schema = schema, queryParams = requestToQueryMap(params))

/**
 * Calls read-only stored procedure [function] (GET) requesting `text/csv`,
 * returning the raw CSV string — the GET counterpart of [rpcCsv].
 */
public suspend fun DatabaseClient.rpcGetCsv(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
): SupabaseResult<String> =
    rpcGet(function = function, schema = schema, queryParams = queryParams, csv = true)

/**
 * Calls read-only stored procedure [function] (GET) requesting CSV with [Map]
 * arguments — the `Map` form of [rpcGetCsv].
 */
public suspend fun DatabaseClient.rpcGetCsv(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
): SupabaseResult<String> =
    rpcGetCsv(function = function, schema = schema, queryParams = queryParams.toPairList())

/**
 * Calls read-only stored procedure [function] (GET) requesting CSV with a
 * serializable [params] object — the typed form of [rpcGetCsv].
 */
public suspend inline fun <reified Request : Any> DatabaseClient.rpcGetCsv(
    function: String,
    schema: String? = null,
    params: Request,
): SupabaseResult<String> =
    rpcGetCsv(function = function, schema = schema, queryParams = requestToQueryMap(params))

/**
 * Issues read-only stored procedure [function] (GET) as a `HEAD` request for its
 * headers/[count] only, discarding the empty body — the GET counterpart of
 * [rpcHead].
 */
public suspend fun DatabaseClient.rpcGetHead(
    function: String,
    schema: String? = null,
    queryParams: List<Pair<String, String>> = emptyList(),
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpcGet(function = function, schema = schema, queryParams = queryParams, head = true, count = count).map { }

/**
 * Issues read-only stored procedure [function] (GET) as a `HEAD` request with
 * [Map] arguments — the `Map` form of [rpcGetHead].
 */
public suspend fun DatabaseClient.rpcGetHead(
    function: String,
    schema: String? = null,
    queryParams: Map<String, String>,
    count: CountOption? = null,
): SupabaseResult<Unit> =
    rpcGetHead(function = function, schema = schema, queryParams = queryParams.toPairList(), count = count)

/**
 * Issues read-only stored procedure [function] (GET) as a `HEAD` request with a
 * serializable [params] object — the typed form of [rpcGetHead].
 */
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
    // A `null` argument is omitted from the query map entirely so the function
    // receives its SQL default (or no argument) rather than the literal string
    // `"null"` (`JsonNull.content == "null"`).
    return buildMap {
        for ((key, value) in element.entries) {
            val encoded = jsonElementToQueryValue(value) ?: continue
            put(key, encoded)
        }
    }
}

@PublishedApi
internal fun jsonElementToQueryValue(value: JsonElement): String? =
    when (value) {
        // `JsonNull` is a `JsonPrimitive`, so it must be checked first; a null arg
        // is omitted (returns `null`) rather than emitting the string `"null"`.
        is JsonNull -> null
        is JsonObject -> defaultJson.encodeToString(value)
        is kotlinx.serialization.json.JsonPrimitive -> value.content
        else -> defaultJson.encodeToString(value)
    }
