package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * Internal implementation of [DatabaseClient] backed by [SupabaseClient].
 *
 * All requests target the PostgREST REST v1 endpoints. Filters, ordering,
 * and pagination are translated into query-string parameters; PostgREST
 * preference headers control return representation and count behaviour.
 */
internal class DatabaseClientImpl(
    private val client: SupabaseClient,
) : DatabaseClient {

    override suspend fun select(
        table: String,
        columns: String,
        head: Boolean,
        count: CountOption?,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        val queryParams = buildList {
            add("select" to columns)
            addAll(FilterBuilder().apply(filters).build())
        }
        val headers = buildPreferHeader {
            count?.let { add("count=${it.headerValue}") }
        }

        if (head) {
            // HEAD-style: use GET with Accept that returns no body parsing,
            // but PostgREST still returns the count in the Content-Range header.
            // We return empty success since the caller only wants the count.
            client.get(
                endpoint = "/rest/v1/$table",
                queryParams = queryParams,
                headers = headers,
            )
            return SupabaseResult.Success("")
        }

        return client.get(
            endpoint = "/rest/v1/$table",
            queryParams = queryParams,
            headers = headers + ("Accept" to "application/json"),
        )
    }

    override suspend fun insert(
        table: String,
        body: String,
        upsert: Boolean,
        onConflict: String?,
        returning: ReturnOption,
        count: CountOption?,
    ): SupabaseResult<String> {
        val endpoint = buildEndpoint("/rest/v1/$table") {
            if (onConflict != null) add("on_conflict" to onConflict)
        }
        val headers = buildPreferHeader {
            add("return=${returning.headerValue}")
            count?.let { add("count=${it.headerValue}") }
            if (upsert) add("resolution=merge-duplicates")
        }

        return client.post(
            endpoint = endpoint,
            body = body,
            headers = headers + jsonContentHeaders(),
        )
    }

    override suspend fun update(
        table: String,
        body: String,
        returning: ReturnOption,
        count: CountOption?,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("/rest/v1/$table", filterParams)
        val headers = buildPreferHeader {
            add("return=${returning.headerValue}")
            count?.let { add("count=${it.headerValue}") }
        }

        return client.patch(
            endpoint = endpoint,
            body = body,
            headers = headers + jsonContentHeaders(),
        )
    }

    override suspend fun delete(
        table: String,
        returning: ReturnOption,
        count: CountOption?,
        filters: FilterBuilder.() -> Unit,
    ): SupabaseResult<String> {
        val filterParams = FilterBuilder().apply(filters).build()
        val endpoint = buildEndpoint("/rest/v1/$table", filterParams)
        val headers = buildPreferHeader {
            add("return=${returning.headerValue}")
            count?.let { add("count=${it.headerValue}") }
        }

        return client.delete(
            endpoint = endpoint,
            headers = headers,
        )
    }

    override suspend fun rpc(
        function: String,
        params: String?,
        head: Boolean,
        count: CountOption?,
    ): SupabaseResult<String> {
        val headers = buildPreferHeader {
            count?.let { add("count=${it.headerValue}") }
        }

        if (head) {
            client.post(
                endpoint = "/rest/v1/rpc/$function",
                body = params,
                headers = headers,
            )
            return SupabaseResult.Success("")
        }

        return client.post(
            endpoint = "/rest/v1/rpc/$function",
            body = params,
            headers = headers + jsonContentHeaders(),
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Builds the `Prefer` header from a list of directives.
     * Returns an empty map when no directives are present.
     */
    private inline fun buildPreferHeader(
        block: MutableList<String>.() -> Unit,
    ): Map<String, String> {
        val directives = buildList(block)
        return if (directives.isEmpty()) emptyMap()
        else mapOf("Prefer" to directives.joinToString(", "))
    }

    /**
     * Appends query parameters to an endpoint path.
     *
     * [SupabaseClient.post], [patch], and [delete] do not accept a separate
     * `queryParams` argument, so we encode them directly into the URL.
     */
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
        val qs = all.joinToString("&") { (k, v) -> "$k=$v" }
        return "$base?$qs"
    }

    private fun jsonContentHeaders(): Map<String, String> =
        mapOf("Content-Type" to "application/json")
}
