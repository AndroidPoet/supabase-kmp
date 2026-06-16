package io.github.androidpoet.supabase.realtime

/** DSL marker for [RealtimeFilter] so member operators don't leak into nested scopes. */
@DslMarker
public annotation class RealtimeFilterDsl

/**
 * Builds the single server-side filter for a `postgres_changes` subscription
 * without hand-writing the `column=op.value` wire string (and getting the
 * escaping or operator name subtly wrong).
 *
 * Realtime supports exactly **one** filter per change subscription and a fixed
 * operator set (`eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`) — setting more than
 * one throws. Use it via [realtimeFilter] or the typed `onPostgresChange`
 * overloads:
 *
 * ```
 * channel.onPostgresChange(table = "messages", filter = realtimeFilter { eq("room_id", roomId) }) { json -> … }
 * ```
 */
@RealtimeFilterDsl
public class RealtimeFilter {
    private var built: String? = null

    private fun set(column: String, op: String, value: String) {
        check(built == null) {
            "Realtime supports only a single filter per postgres_changes subscription; remove the extra filter call"
        }
        require(column.isNotBlank()) { "filter column must not be blank" }
        built = "$column=$op.$value"
    }

    public fun eq(column: String, value: String): Unit = set(column, "eq", value)

    public fun eq(column: String, value: Number): Unit = set(column, "eq", value.toString())

    public fun eq(column: String, value: Boolean): Unit = set(column, "eq", value.toString())

    public fun neq(column: String, value: String): Unit = set(column, "neq", value)

    public fun neq(column: String, value: Number): Unit = set(column, "neq", value.toString())

    public fun neq(column: String, value: Boolean): Unit = set(column, "neq", value.toString())

    public fun gt(column: String, value: Number): Unit = set(column, "gt", value.toString())

    public fun gte(column: String, value: Number): Unit = set(column, "gte", value.toString())

    public fun lt(column: String, value: Number): Unit = set(column, "lt", value.toString())

    public fun lte(column: String, value: Number): Unit = set(column, "lte", value.toString())

    /** `column IN (…)` — e.g. `isIn("status", listOf("open", "pending"))`. */
    public fun isIn(column: String, values: List<String>): Unit =
        set(column, "in", "(${values.joinToString(",")})")

    /** The built `column=op.value` filter string, or `null` if no operator was set. */
    public fun build(): String? = built
}

/** Builds a single `postgres_changes` filter string. See [RealtimeFilter]. */
public inline fun realtimeFilter(block: RealtimeFilter.() -> Unit): String? =
    RealtimeFilter().apply(block).build()
