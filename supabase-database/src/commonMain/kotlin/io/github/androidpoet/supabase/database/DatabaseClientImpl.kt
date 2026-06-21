package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val INTERNAL_RETRY_HEADER = "X-Supabase-Kmp-Retry"

internal class DatabaseClientImpl(
    private val client: SupabaseClient,
) : DatabaseClient {
    override suspend fun select(
        table: String,
        schema: String?,
        columns: String,
        head: Boolean,
        single: Boolean,
        csv: Boolean,
        geojson: Boolean,
        count: CountOption?,
        stripNulls: Boolean,
        explain: ExplainOptions?,
        retry: Boolean,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        require(!(single && csv)) { "single and csv cannot both be true" }
        require(!(csv && stripNulls)) { "stripNulls cannot be used with csv" }
        require(!(geojson && stripNulls)) { "stripNulls cannot be used with geojson" }
        require(!(geojson && csv)) { "geojson and csv cannot both be true" }
        require(!(geojson && single)) { "geojson and single cannot both be true" }
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val queryParams =
            buildList {
                add("select" to columns)
                addAll(FilterBuilder().apply(filters).build())
            }
        val requestHeaders =
            headers +
                buildPreferHeader {
                    count?.let { add("count=${it.headerValue}") }
                } + retryHeader(retry)
        val headersWithSchema = addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = true)
        if (head) {
            // A head request must not transfer (and then discard) the whole result
            // set: issue a real HTTP HEAD so the server computes count/headers
            // without serializing rows, mirroring selectCount.
            val acceptHead = headersWithSchema + ("Accept" to acceptHeader(single, csv, stripNulls, explain, geojson))
            val result =
                client.rawRequest(
                    method = SupabaseHttpMethod.HEAD,
                    url = buildEndpoint("${DatabasePaths.BASE}/$safeTable", queryParams),
                    headers = acceptHead,
                )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        return client.get(
            endpoint = "${DatabasePaths.BASE}/$safeTable",
            queryParams = queryParams,
            headers = headersWithSchema + ("Accept" to acceptHeader(single, csv, stripNulls, explain, geojson)),
        )
    }

    override suspend fun selectCount(
        table: String,
        schema: String?,
        columns: String,
        count: CountOption,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<PostgrestRange> {
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val queryParams =
            buildList {
                add("select" to columns)
                addAll(FilterBuilder().apply(filters).build())
            }
        val requestHeaders =
            headers +
                buildPreferHeader { add("count=${count.headerValue}") }
        val headersWithSchema =
            addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = true) +
                ("Accept" to acceptHeader())
        val endpoint = buildEndpoint("${DatabasePaths.BASE}/$safeTable", queryParams)
        return when (
            val result =
                client.rawRequest(
                    method = SupabaseHttpMethod.HEAD,
                    url = endpoint,
                    headers = headersWithSchema,
                )
        ) {
            is SupabaseResult.Success -> SupabaseResult.Success(parseContentRange(result.value.header("Content-Range")))
            is SupabaseResult.Failure -> result
        }
    }

    override suspend fun selectRange(
        table: String,
        schema: String?,
        columns: String,
        single: Boolean,
        count: CountOption,
        stripNulls: Boolean,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<Pair<String, PostgrestRange>> {
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val queryParams =
            buildList {
                add("select" to columns)
                addAll(FilterBuilder().apply(filters).build())
            }
        val requestHeaders =
            headers +
                buildPreferHeader { add("count=${count.headerValue}") }
        val headersWithSchema =
            addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = true) +
                ("Accept" to acceptHeader(single = single, stripNulls = stripNulls))
        val endpoint = buildEndpoint("${DatabasePaths.BASE}/$safeTable", queryParams)
        return when (
            val result =
                client.rawRequest(
                    method = SupabaseHttpMethod.GET,
                    url = endpoint,
                    headers = headersWithSchema,
                )
        ) {
            is SupabaseResult.Success ->
                SupabaseResult.Success(
                    result.value.body.decodeToString() to parseContentRange(result.value.header("Content-Range")),
                )
            is SupabaseResult.Failure -> result
        }
    }

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
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val effectiveColumns = if (!columns.isNullOrEmpty()) columns else deriveBulkInsertColumns(body)
        val endpoint =
            buildEndpoint("${DatabasePaths.BASE}/$safeTable") {
                if (onConflict != null) add("on_conflict" to onConflict)
                if (effectiveColumns.isNotEmpty()) add("columns" to effectiveColumns.joinToString(","))
            }
        val requestHeaders =
            headers +
                buildPreferHeader {
                    add("return=${returning.headerValue}")
                    count?.let { add("count=${it.headerValue}") }
                    if (upsert) add("resolution=${upsertResolution.headerValue}")
                    if (!defaultToNull) add("missing=default")
                    if (rollback) add("tx=rollback")
                }
        return client.post(
            endpoint = endpoint,
            body = body,
            headers =
                addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = false) +
                    contentTypeHeaders(contentType) +
                    ("Accept" to acceptHeader(stripNulls = stripNulls)),
        )
    }

    // PostgREST infers the insert columns from the FIRST row only. For a bulk insert
    // whose rows carry different key sets, columns missing from row 0 are silently
    // dropped. Sending the union of every row's keys as `columns=` forces all of them
    // to be considered (rows omitting one fall back to DEFAULT/NULL). A single row has
    // no such ambiguity, so we only derive for an array of more than one object and
    // never override a caller-supplied `columns`.
    private fun deriveBulkInsertColumns(body: String): List<String> =
        runCatching {
            val array = defaultJson.parseToJsonElement(body) as? JsonArray ?: return@runCatching emptyList()
            if (array.size <= 1) return@runCatching emptyList()
            array.flatMap { (it as? JsonObject)?.keys ?: emptySet() }.distinct()
        }.getOrDefault(emptyList())

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
    ): SupabaseResult<String> {
        maxAffected?.let { require(it > 0) { "maxAffected must be greater than 0" } }
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("${DatabasePaths.BASE}/$safeTable", filterParams)
        val requestHeaders =
            headers +
                buildPreferHeader {
                    add("return=${returning.headerValue}")
                    count?.let { add("count=${it.headerValue}") }
                    if (rollback) add("tx=rollback")
                    maxAffected?.let {
                        add("handling=strict")
                        add("max-affected=$it")
                    }
                }
        return client.patch(
            endpoint = endpoint,
            body = body,
            headers =
                addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = false) +
                    contentTypeHeaders() +
                    ("Accept" to acceptHeader(stripNulls = stripNulls, explain = explain)),
        )
    }

    override suspend fun replace(
        table: String,
        body: String,
        returning: ReturnOption,
        columns: String,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        val safeTable = validatePathSegment(table, "table")
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint =
            buildEndpoint("${DatabasePaths.BASE}/$safeTable", filterParams) {
                add("select" to columns)
            }
        val requestHeaders =
            buildPreferHeader {
                add("return=${returning.headerValue}")
            }
        return client.put(
            endpoint = endpoint,
            body = body,
            headers =
                requestHeaders +
                    contentTypeHeaders() +
                    ("Accept" to acceptHeader()),
        )
    }

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
    ): SupabaseResult<String> {
        maxAffected?.let { require(it > 0) { "maxAffected must be greater than 0" } }
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("${DatabasePaths.BASE}/$safeTable", filterParams)
        val requestHeaders =
            headers +
                buildPreferHeader {
                    add("return=${returning.headerValue}")
                    count?.let { add("count=${it.headerValue}") }
                    if (rollback) add("tx=rollback")
                    maxAffected?.let {
                        add("handling=strict")
                        add("max-affected=$it")
                    }
                }
        return client.delete(
            endpoint = endpoint,
            headers =
                addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = false) +
                    ("Accept" to acceptHeader(stripNulls = stripNulls, explain = explain)),
        )
    }

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
        contentType: String,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        require(!(single && csv)) { "single and csv cannot both be true" }
        require(!(csv && stripNulls)) { "stripNulls cannot be used with csv" }
        maxAffected?.let { require(it > 0) { "maxAffected must be greater than 0" } }
        val safeFunction = validatePathSegment(function, "function")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("${DatabasePaths.RPC}/$safeFunction", filterParams)
        val requestHeaders =
            headers +
                buildPreferHeader {
                    count?.let { add("count=${it.headerValue}") }
                    if (rollback) add("tx=rollback")
                    maxAffected?.let {
                        add("handling=strict")
                        add("max-affected=$it")
                    }
                }
        if (head) {
            // A head RPC is still an HTTP POST, so the request schema is selected by
            // Content-Profile (isReadRequest = false) — PostgREST ignores Accept-Profile
            // on a POST — and the JSON/custom body needs its Content-Type, exactly like
            // the non-head path. Only the response body is discarded (count comes back in
            // the Prefer/Content-Range path).
            val result =
                client.post(
                    endpoint = endpoint,
                    body = params,
                    headers =
                        addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = false) +
                            contentTypeHeaders(contentType) +
                            ("Accept" to acceptHeader(single, csv, stripNulls, explain)),
                )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        return client.post(
            endpoint = endpoint,
            body = params,
            headers =
                addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = false) +
                    contentTypeHeaders(contentType) +
                    ("Accept" to acceptHeader(single, csv, stripNulls, explain)),
        )
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
        require(!(single && csv)) { "single and csv cannot both be true" }
        require(!(csv && stripNulls)) { "stripNulls cannot be used with csv" }
        val safeFunction = validatePathSegment(function, "function")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val requestHeaders =
            headers +
                buildPreferHeader {
                    count?.let { add("count=${it.headerValue}") }
                } + retryHeader(retry)
        val readHeaders = addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = true)
        val endpoint = buildEndpoint("${DatabasePaths.RPC}/$safeFunction", queryParams)
        if (head) {
            val result =
                client.get(
                    endpoint = endpoint,
                    headers = readHeaders + ("Accept" to acceptHeader(single, csv, stripNulls, explain)),
                )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        return client.get(
            endpoint = endpoint,
            headers = readHeaders + ("Accept" to acceptHeader(single, csv, stripNulls, explain)),
        )
    }

    // Parses PostgREST's `Content-Range` header (`<range>/<total>`), e.g.
    // `0-9/27` -> range 0..9, count 27; `*/27` -> count 27, no range;
    // `*/*` or a malformed/absent header -> both null. Never throws.
    private fun parseContentRange(header: String?): PostgrestRange {
        if (header.isNullOrBlank()) return PostgrestRange()
        val slash = header.indexOf('/')
        if (slash < 0) return PostgrestRange()
        val rangePart = header.substring(0, slash).trim()
        val totalPart = header.substring(slash + 1).trim()
        val count = if (totalPart == "*") null else totalPart.toLongOrNull()
        val range =
            if (rangePart == "*") {
                null
            } else {
                val dash = rangePart.indexOf('-')
                if (dash < 0) {
                    null
                } else {
                    val from = rangePart.substring(0, dash).trim().toLongOrNull()
                    val to = rangePart.substring(dash + 1).trim().toLongOrNull()
                    if (from != null && to != null && to >= from) from..to else null
                }
            }
        return PostgrestRange(count = count, range = range)
    }

    private inline fun buildPreferHeader(
        block: MutableList<String>.() -> Unit,
    ): Map<String, String> {
        val directives = buildList(block)
        return if (directives.isEmpty()) {
            emptyMap()
        } else {
            mapOf("Prefer" to directives.joinToString(", "))
        }
    }

    private fun buildEndpoint(
        base: String,
        params: List<Pair<String, String>> = emptyList(),
        extra: MutableList<Pair<String, String>>.() -> Unit = {},
    ): String {
        val all =
            buildList {
                addAll(params)
                extra()
            }
        if (all.isEmpty()) return base
        val qs =
            all.joinToString("&") { (k, v) ->
                "${encodeQueryComponent(k)}=${encodeQueryComponent(v)}"
            }
        return "$base?$qs"
    }

    private fun contentTypeHeaders(contentType: String = "application/json"): Map<String, String> =
        mapOf("Content-Type" to contentType)

    private fun retryHeader(enabled: Boolean): Map<String, String> =
        mapOf(INTERNAL_RETRY_HEADER to enabled.toString())

    private fun acceptHeader(
        single: Boolean = false,
        csv: Boolean = false,
        stripNulls: Boolean = false,
        explain: ExplainOptions? = null,
        geojson: Boolean = false,
    ): String {
        val base =
            when {
                single && stripNulls -> "application/vnd.pgrst.object+json;nulls=stripped"
                single -> "application/vnd.pgrst.object+json"
                csv -> "text/csv"
                geojson -> "application/geo+json"
                stripNulls -> "application/vnd.pgrst.array+json;nulls=stripped"
                else -> "application/json"
            }
        return explain?.acceptHeader(base) ?: base
    }

    private fun ExplainOptions.acceptHeader(baseMediaType: String): String {
        val options =
            buildList {
                if (analyze) add("analyze")
                if (verbose) add("verbose")
                if (settings) add("settings")
                if (buffers) add("buffers")
                if (wal) add("wal")
            }.joinToString("|")
        // The plan is emitted in `text` by default, which carries NO media-type
        // suffix; only the non-text formats append `+<format>`. And `options=` is
        // included only when at least one flag is set — an empty `options=` (or a
        // trailing `;`) is malformed and rejected by the server.
        val suffix = if (format == ExplainFormat.TEXT) "" else "+${format.headerValue}"
        val optionsPart = if (options.isEmpty()) "" else "; options=$options"
        return "application/vnd.pgrst.plan$suffix; for=\"$baseMediaType\"$optionsPart"
    }

    private fun addSchemaHeaders(
        headers: Map<String, String>,
        schema: String?,
        isReadRequest: Boolean,
    ): Map<String, String> {
        if (schema == null) return headers
        val profileHeader = if (isReadRequest) "Accept-Profile" else "Content-Profile"
        return headers + (profileHeader to schema)
    }

    private fun validatePathSegment(value: String, label: String): String {
        val isValid =
            value.isNotBlank() && value.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }
        require(isValid) { "Invalid $label: $value" }
        return value
    }

    private fun encodeQueryComponent(value: String): String {
        val bytes = value.encodeToByteArray()
        val out = StringBuilder(bytes.size)
        bytes.forEach { b ->
            val c = b.toInt().toChar()
            if (c.isAsciiUnreserved()) {
                out.append(c)
            } else {
                val v = b.toInt() and 0xff
                out.append('%')
                out.append("0123456789ABCDEF"[v ushr 4])
                out.append("0123456789ABCDEF"[v and 0x0f])
            }
        }
        return out.toString()
    }

    private fun Char.isAsciiUnreserved(): Boolean =
        (this in 'a'..'z') ||
            (this in 'A'..'Z') ||
            (this in '0'..'9') ||
            this == '-' ||
            this == '_' ||
            this == '.' ||
            this == '~'
}
