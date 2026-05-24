package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult

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
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        require(!(single && csv)) { "single and csv cannot both be true" }
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val queryParams = buildList {
            add("select" to columns)
            addAll(FilterBuilder().apply(filters).build())
        }
        val headers = buildPreferHeader {
            count?.let { add("count=${it.headerValue}") }
        }
        val headersWithSchema = addSchemaHeaders(headers, safeSchema, isReadRequest = true)
        if (head) {
            val result = client.get(
                endpoint = "/rest/v1/$safeTable",
                queryParams = queryParams,
                headers = headersWithSchema,
            )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        val acceptHeader = when {
            single -> "application/vnd.pgrst.object+json"
            csv -> "text/csv"
            else -> "application/json"
        }
        return client.get(
            endpoint = "/rest/v1/$safeTable",
            queryParams = queryParams,
            headers = headersWithSchema + ("Accept" to acceptHeader),
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
    ): SupabaseResult<String> {
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val endpoint = buildEndpoint("/rest/v1/$safeTable") {
            if (onConflict != null) add("on_conflict" to onConflict)
            if (!columns.isNullOrEmpty()) add("columns" to columns.joinToString(","))
        }
        val headers = buildPreferHeader {
            add("return=${returning.headerValue}")
            count?.let { add("count=${it.headerValue}") }
            if (upsert) add("resolution=${upsertResolution.headerValue}")
            if (!defaultToNull) add("missing=default")
        }
        return client.post(
            endpoint = endpoint,
            body = body,
            headers = addSchemaHeaders(headers, safeSchema, isReadRequest = false) + jsonContentHeaders(),
        )
    }

    override suspend fun update(
        table: String,
        schema: String?,
        body: String,
        returning: ReturnOption,
        count: CountOption?,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("/rest/v1/$safeTable", filterParams)
        val headers = buildPreferHeader {
            add("return=${returning.headerValue}")
            count?.let { add("count=${it.headerValue}") }
        }
        return client.patch(
            endpoint = endpoint,
            body = body,
            headers = addSchemaHeaders(headers, safeSchema, isReadRequest = false) + jsonContentHeaders(),
        )
    }

    override suspend fun delete(
        table: String,
        schema: String?,
        returning: ReturnOption,
        count: CountOption?,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        val safeTable = validatePathSegment(table, "table")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("/rest/v1/$safeTable", filterParams)
        val headers = buildPreferHeader {
            add("return=${returning.headerValue}")
            count?.let { add("count=${it.headerValue}") }
        }
        return client.delete(
            endpoint = endpoint,
            headers = addSchemaHeaders(headers, safeSchema, isReadRequest = false),
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
    ): SupabaseResult<String> {
        require(!(single && csv)) { "single and csv cannot both be true" }
        val safeFunction = validatePathSegment(function, "function")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val headers = buildPreferHeader {
            count?.let { add("count=${it.headerValue}") }
        }
        if (head) {
            val result = client.post(
                endpoint = "/rest/v1/rpc/$safeFunction",
                body = params,
                headers = addSchemaHeaders(headers, safeSchema, isReadRequest = true),
            )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        val acceptHeader = when {
            single -> "application/vnd.pgrst.object+json"
            csv -> "text/csv"
            else -> "application/json"
        }
        return client.post(
            endpoint = "/rest/v1/rpc/$safeFunction",
            body = params,
            headers = addSchemaHeaders(headers, safeSchema, isReadRequest = false) +
                jsonContentHeaders() +
                ("Accept" to acceptHeader),
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
    ): SupabaseResult<String> {
        require(!(single && csv)) { "single and csv cannot both be true" }
        val safeFunction = validatePathSegment(function, "function")
        val safeSchema = schema?.let { validatePathSegment(it, "schema") }
        val headers = buildPreferHeader {
            count?.let { add("count=${it.headerValue}") }
        }
        val readHeaders = addSchemaHeaders(headers, safeSchema, isReadRequest = true)
        val endpoint = buildEndpoint("/rest/v1/rpc/$safeFunction", queryParams)
        if (head) {
            val result = client.get(
                endpoint = endpoint,
                headers = readHeaders,
            )
            return when (result) {
                is SupabaseResult.Success -> SupabaseResult.Success("")
                is SupabaseResult.Failure -> result
            }
        }
        val acceptHeader = when {
            single -> "application/vnd.pgrst.object+json"
            csv -> "text/csv"
            else -> "application/json"
        }
        return client.get(
            endpoint = endpoint,
            headers = readHeaders + ("Accept" to acceptHeader),
        )
    }

    private inline fun buildPreferHeader(
        block: MutableList<String>.() -> Unit,
    ): Map<String, String> {
        val directives = buildList(block)
        return if (directives.isEmpty()) emptyMap()
        else mapOf("Prefer" to directives.joinToString(", "))
    }

    private fun buildEndpoint(
        base: String,
        params: List<Pair<String, String>> = emptyList(),
        extra: MutableList<Pair<String, String>>.() -> Unit = {},
    ): String {
        val all = buildList {
            addAll(params)
            extra()
        }
        if (all.isEmpty()) return base
        val qs = all.joinToString("&") { (k, v) ->
            "${encodeQueryComponent(k)}=${encodeQueryComponent(v)}"
        }
        return "$base?$qs"
    }

    private fun jsonContentHeaders(): Map<String, String> =
        mapOf("Content-Type" to "application/json")

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
