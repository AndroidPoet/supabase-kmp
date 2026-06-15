package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult

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
        count: CountOption?,
        stripNulls: Boolean,
        explain: ExplainOptions?,
        retry: Boolean,
        headers: Map<String, String>,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        require(!(single && csv)) { "single and csv cannot both be true" }
        require(!(csv && stripNulls)) { "stripNulls cannot be used with csv" }
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
            val result =
                client.get(
                    endpoint = "/rest/v1/$safeTable",
                    queryParams = queryParams,
                    headers = headersWithSchema + ("Accept" to acceptHeader(single, csv, stripNulls, explain)),
                )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        return client.get(
            endpoint = "/rest/v1/$safeTable",
            queryParams = queryParams,
            headers = headersWithSchema + ("Accept" to acceptHeader(single, csv, stripNulls, explain)),
        )
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
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val endpoint =
            buildEndpoint("/rest/v1/$safeTable") {
                if (onConflict != null) add("on_conflict" to onConflict)
                if (!columns.isNullOrEmpty()) add("columns" to columns.joinToString(","))
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
                    jsonContentHeaders() +
                    ("Accept" to acceptHeader(stripNulls = stripNulls)),
        )
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
    ): SupabaseResult<String> {
        maxAffected?.let { require(it > 0) { "maxAffected must be greater than 0" } }
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("/rest/v1/$safeTable", filterParams)
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
                    jsonContentHeaders() +
                    ("Accept" to acceptHeader(stripNulls = stripNulls, explain = explain)),
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
        val endpoint = buildEndpoint("/rest/v1/$safeTable", filterParams)
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
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        require(!(single && csv)) { "single and csv cannot both be true" }
        require(!(csv && stripNulls)) { "stripNulls cannot be used with csv" }
        maxAffected?.let { require(it > 0) { "maxAffected must be greater than 0" } }
        val safeFunction = validatePathSegment(function, "function")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
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
            val result =
                client.post(
                    endpoint = "/rest/v1/rpc/$safeFunction",
                    body = params,
                    headers =
                        addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = true) +
                            ("Accept" to acceptHeader(single, csv, stripNulls, explain)),
                )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        return client.post(
            endpoint = "/rest/v1/rpc/$safeFunction",
            body = params,
            headers =
                addSchemaHeaders(requestHeaders, safeSchema, isReadRequest = false) +
                    jsonContentHeaders() +
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
        val endpoint = buildEndpoint("/rest/v1/rpc/$safeFunction", queryParams)
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

    private fun jsonContentHeaders(): Map<String, String> =
        mapOf("Content-Type" to "application/json")

    private fun retryHeader(enabled: Boolean): Map<String, String> =
        mapOf(INTERNAL_RETRY_HEADER to enabled.toString())

    private fun acceptHeader(
        single: Boolean = false,
        csv: Boolean = false,
        stripNulls: Boolean = false,
        explain: ExplainOptions? = null,
    ): String {
        val base =
            when {
                single && stripNulls -> "application/vnd.pgrst.object+json;nulls=stripped"
                single -> "application/vnd.pgrst.object+json"
                csv -> "text/csv"
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
        return "application/vnd.pgrst.plan+${format.headerValue}; for=\"$baseMediaType\"; options=$options;"
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
