package io.github.androidpoet.supabase.database
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * How PostgREST should compute the total row count, sent as the `count=`
 * directive of the `Prefer` header.
 *
 * The resulting total is surfaced in the `Content-Range` response header and
 * parsed into [PostgrestRange]/[PostgrestPage]. [EXACT] runs a full count and is
 * accurate but slowest; [PLANNED] and [ESTIMATED] read the query planner's
 * estimate (and, for [ESTIMATED], may fall back to an exact count for small
 * results), trading precision for speed on large tables.
 */
public enum class CountOption(
    internal val headerValue: String,
) {
    EXACT("exact"),
    PLANNED("planned"),
    ESTIMATED("estimated"),
}

/**
 * What a write should return, sent as the `return=` directive of the `Prefer`
 * header.
 *
 * [REPRESENTATION] makes PostgREST echo the affected rows back in the body (so
 * the typed helpers can decode them); [MINIMAL] returns an empty body, which the
 * `*Unit` helpers use when the result is not needed; [HEADERS_ONLY] also returns
 * an empty body but keeps response headers such as `Location` populated.
 */
public enum class ReturnOption(
    internal val headerValue: String,
) {
    MINIMAL("minimal"),
    REPRESENTATION("representation"),
    HEADERS_ONLY("headers-only"),
}

/**
 * How an upsert resolves a conflict on the target key, sent as the `resolution=`
 * directive of the `Prefer` header when `upsert` is enabled on [DatabaseClient.insert].
 *
 * [MERGE_DUPLICATES] updates the existing row with the incoming values;
 * [IGNORE_DUPLICATES] leaves the existing row untouched.
 */
public enum class UpsertResolution(
    internal val headerValue: String,
) {
    MERGE_DUPLICATES("merge-duplicates"),
    IGNORE_DUPLICATES("ignore-duplicates"),
}

/**
 * Output format for an `EXPLAIN` plan, selecting the `application/vnd.pgrst.plan+<format>`
 * media type requested via [ExplainOptions].
 */
public enum class ExplainFormat(
    internal val headerValue: String,
) {
    TEXT("text"),
    JSON("json"),
    XML("xml"),
    YAML("yaml"),
}

/**
 * Requests a PostgREST query plan (`EXPLAIN`) instead of executing the request
 * for its result, mapping to the `application/vnd.pgrst.plan` Accept media type
 * and its `options=` flags.
 *
 * Pass an instance to [DatabaseClient.select], [DatabaseClient.update],
 * [DatabaseClient.delete], or the `rpc` calls to have them return the plan text
 * in the body rather than rows. Each flag toggles the matching `EXPLAIN` option;
 * [format] picks the rendering (see [ExplainFormat]).
 *
 * @param analyze actually run the query and report real timings, not just the estimated plan.
 * @param verbose include additional per-node detail.
 * @param settings include configuration parameters that affect planning.
 * @param buffers include buffer usage (requires [analyze]).
 * @param wal include write-ahead-log usage (requires [analyze]).
 */
public data class ExplainOptions(
    public val analyze: Boolean = false,
    public val verbose: Boolean = false,
    public val settings: Boolean = false,
    public val buffers: Boolean = false,
    public val wal: Boolean = false,
    public val format: ExplainFormat = ExplainFormat.TEXT,
)

/**
 * The count and row range parsed from a PostgREST `Content-Range` response
 * header (returned when a request sets `count=`). For `Content-Range: 0-9/27`,
 * [range] is `0..9` and [count] is `27`. Either part may be absent — `*` in the
 * header maps to `null` rather than failing — so an unparseable or count-only
 * header still yields a value.
 */
public data class PostgrestRange(
    public val count: Long? = null,
    public val range: LongRange? = null,
)

/**
 * A page of decoded rows together with the total [count] reported by PostgREST.
 * Produced by the `*WithCount` helpers, which issue the normal request with a
 * `count=` preference and surface the `Content-Range` total alongside the data.
 */
public data class PostgrestPage<T>(
    public val rows: List<T>,
    public val count: Long? = null,
    public val range: LongRange? = null,
)

/**
 * Thin, stateless client over a Supabase project's PostgREST (`/rest/v1`) API.
 *
 * Every call maps to a single HTTP request and returns the raw response body as
 * a [String] inside a [SupabaseResult] — failures (HTTP errors, transport
 * problems, malformed requests) are returned as [SupabaseResult.Failure] carrying
 * a typed [io.github.androidpoet.supabase.core.result.SupabaseError], never
 * thrown. These string-returning methods are the wire-level core; for ergonomic,
 * type-safe access prefer the `reified` extensions in `DatabaseClientExt`
 * ([selectTyped], [insertTyped], [rpcTyped], etc.), which serialize/deserialize
 * around them. Obtain an instance via [createDatabaseClient] or the
 * [SupabaseClient.database] accessor.
 *
 * [WHERE]-style row filters are expressed through the [FilterBuilder] receiver
 * and translated to PostgREST query parameters; common options surface as the
 * `Prefer` header ([CountOption], [ReturnOption], [UpsertResolution]).
 */
public interface DatabaseClient {
    /**
     * Reads rows from [table] via `GET /rest/v1/{table}`, returning the response
     * body as a raw string (typically a JSON array).
     *
     * The [columns] selector becomes `select=` (supporting embedded resources and
     * renames); [filters] add the row predicates, ordering, and range. The body
     * shape is chosen by the request's `Accept` header: [single] expects exactly
     * one row (`application/vnd.pgrst.object+json`, a 406 if not), [csv] returns
     * `text/csv`, [geojson] returns a PostGIS `FeatureCollection`, and [stripNulls]
     * omits null fields. [head] issues the request for its headers only and yields
     * an empty body — pair it with [count] to fetch just a total. [explain] returns
     * the query plan instead of data. Mutually exclusive combinations (e.g. [single]
     * with [csv]) are rejected before the request is sent.
     *
     * @param schema target Postgres schema; sent as `Accept-Profile` when non-null, otherwise the default schema.
     * @param count adds a `count=` preference so the total appears in `Content-Range`.
     * @param retry whether this read may be transparently retried by the transport.
     * @param headers extra request headers, merged with the computed ones.
     */
    public suspend fun select(
        table: String,
        schema: String? = null,
        columns: String = "*",
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        geojson: Boolean = false,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        explain: ExplainOptions? = null,
        retry: Boolean = true,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Issues a count-only `HEAD` request and returns the total reported by
     * PostgREST in the `Content-Range` header. No rows are fetched. [count]
     * defaults to [CountOption.EXACT]; use [CountOption.PLANNED] or
     * [CountOption.ESTIMATED] to trade accuracy for speed on large tables.
     */
    public suspend fun selectCount(
        table: String,
        schema: String? = null,
        columns: String = "*",
        count: CountOption = CountOption.EXACT,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<PostgrestRange>

    /**
     * Like [select], but also returns the total [PostgrestRange.count] and the
     * fetched [PostgrestRange.range] from the `Content-Range` header alongside
     * the (string) body. Use the `selectWithCount` typed helper to decode rows
     * into a [PostgrestPage].
     */
    public suspend fun selectRange(
        table: String,
        schema: String? = null,
        columns: String = "*",
        single: Boolean = false,
        count: CountOption = CountOption.EXACT,
        stripNulls: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<Pair<String, PostgrestRange>>

    /**
     * Inserts (or upserts) into [table] via `POST /rest/v1/{table}`, with [body]
     * being the JSON of a single object or an array for a bulk insert.
     *
     * When [upsert] is set, an `on_conflict=` parameter (from [onConflict]) and a
     * `resolution=` preference ([upsertResolution]) turn this into an upsert.
     * [returning] controls whether the affected rows are echoed back
     * ([ReturnOption.REPRESENTATION]) or the body is empty ([ReturnOption.MINIMAL]);
     * the typed helper [insertTyped] decodes the former while [insertUnit] uses the
     * latter. For a multi-row [body] the union of all rows' keys is sent as
     * `columns=` so columns absent from the first row are not silently dropped; pass
     * [columns] to override. [defaultToNull] = false sends `missing=default` so
     * omitted fields take their column DEFAULT instead of NULL.
     *
     * @param columns explicit column list (`columns=`); when null it is derived for bulk inserts.
     * @param count adds a `count=` preference, surfacing the affected total in `Content-Range`.
     * @param rollback when true sends `tx=rollback` so the write is validated but not committed.
     * @param contentType the request body's `Content-Type`; defaults to JSON, but lets callers send
     *   e.g. a `text/csv` bulk insert instead of a JSON array. Only the header changes — [body] is sent verbatim.
     * @return the response body (representation rows, or empty for [ReturnOption.MINIMAL]).
     */
    public suspend fun insert(
        table: String,
        schema: String? = null,
        body: String,
        columns: List<String>? = null,
        upsert: Boolean = false,
        upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
        defaultToNull: Boolean = true,
        onConflict: String? = null,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /**
     * Updates rows of [table] matching [filters] via `PATCH /rest/v1/{table}`,
     * applying the partial JSON object in [body] to every matched row.
     *
     * Because the rows changed are whatever [filters] select, an empty filter set
     * updates the whole table — set [maxAffected] to cap the change (sends
     * `handling=strict` + `max-affected=`, failing the request rather than touching
     * more rows than allowed). [returning]/[stripNulls] shape the echoed body as in
     * [insert]; the typed helpers [updateTyped]/[updateUnit] sit on top. [explain]
     * returns the query plan instead of mutating.
     *
     * @param count adds a `count=` preference, surfacing the affected total in `Content-Range`.
     * @param rollback when true sends `tx=rollback` so the change is validated but not committed.
     * @param maxAffected must be greater than 0 when set; caps the rows the statement may modify.
     */
    public suspend fun update(
        table: String,
        schema: String? = null,
        body: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        maxAffected: Int? = null,
        explain: ExplainOptions? = null,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Replaces (or inserts) a single row of [table] via `PUT /rest/v1/{table}`,
     * the PostgREST replace-by-primary-key write.
     *
     * Unlike [update]'s partial `PATCH`, this is a full-row replace: [body] must be
     * a JSON object carrying **every** column (including the primary key), and
     * [filters] must select **exactly** that primary key (e.g. `eq("id", 7)` for a
     * row whose body has `"id": 7`). PostgREST replaces the matching row, or inserts
     * the row when none matches. [returning] shapes the echoed body as in [update];
     * [columns] becomes the `select=` projection of any returned representation.
     *
     * @param returning whether the affected row is echoed back ([ReturnOption.REPRESENTATION]) or the body is empty.
     * @param columns the `select=` projection applied to the returned representation.
     * @return the response body (the replaced row, or empty for non-representation returns).
     */
    public suspend fun replace(
        table: String,
        body: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        columns: String = "*",
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Deletes rows of [table] matching [filters] via `DELETE /rest/v1/{table}`.
     *
     * As with [update], the rows removed are exactly those [filters] select, so an
     * empty filter set deletes the whole table — guard large or accidental deletes
     * with [maxAffected] (`handling=strict` + `max-affected=`, which fails the
     * request when more rows would be affected). [returning] decides whether the
     * deleted rows are echoed back ([deleteTyped]) or the body is empty
     * ([deleteUnit]); [explain] returns the query plan instead of deleting.
     *
     * @param count adds a `count=` preference, surfacing the affected total in `Content-Range`.
     * @param rollback when true sends `tx=rollback` so the delete is validated but not committed.
     * @param maxAffected must be greater than 0 when set; caps the rows the statement may delete.
     */
    public suspend fun delete(
        table: String,
        schema: String? = null,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        maxAffected: Int? = null,
        explain: ExplainOptions? = null,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Calls a stored procedure (`POST /rest/v1/rpc/{function}`) with [params] as the
     * JSON request body, returning the function's result as a raw string.
     *
     * This is the mutating/POST form, suitable for functions with side effects or
     * large argument payloads; for a read-only function prefer [rpcGet], which is
     * cacheable. [single] expects a scalar/object result, [csv] requests `text/csv`,
     * and [explain] returns the plan. The typed wrappers ([rpcTyped],
     * [rpcSingleTyped], [rpcListTyped], [rpcUnit]) decode the body for you.
     *
     * @param params JSON object of named arguments, or null for a no-argument call.
     * @param head issue the request for headers/count only, yielding an empty body.
     * @param count adds a `count=` preference, surfacing the total in `Content-Range`.
     * @param rollback when true sends `tx=rollback` so any writes are rolled back.
     * @param maxAffected must be greater than 0 when set; caps rows the function may modify.
     * @param contentType the request body's `Content-Type`; defaults to JSON, but lets callers send a
     *   scalar-typed body (e.g. `text/plain`, `application/octet-stream`) to a single-parameter function. [params] is sent verbatim.
     * @param filters PostgREST filters/ordering/pagination applied to the rows a set-returning
     *   function returns, threaded into the query string exactly as for [select]/[update].
     */
    public suspend fun rpc(
        function: String,
        schema: String? = null,
        params: String? = null,
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        maxAffected: Int? = null,
        explain: ExplainOptions? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Calls a stored procedure over `GET /rest/v1/rpc/{function}`, passing arguments
     * as [queryParams] rather than a body — the cacheable, side-effect-free
     * counterpart to [rpc] for functions marked `STABLE`/`IMMUTABLE`.
     *
     * Each pair becomes a query-string argument; the extensions in `DatabaseClientExt`
     * accept a `Map` or a serializable request object and flatten it into these pairs.
     * [single], [csv], and [explain] behave as on [rpc]; typed decoding is provided by
     * [rpcGetTyped] and friends.
     *
     * @param head issue the request for headers/count only, yielding an empty body.
     * @param count adds a `count=` preference, surfacing the total in `Content-Range`.
     * @param retry whether this read may be transparently retried by the transport.
     */
    public suspend fun rpcGet(
        function: String,
        schema: String? = null,
        queryParams: List<Pair<String, String>> = emptyList(),
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        explain: ExplainOptions? = null,
        retry: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>
}
