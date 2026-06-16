package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Registers a `postgres_changes` callback that decodes each changed row into [T]
 * before handing it to [onRow], so you work with your own model instead of a raw
 * `JsonObject`. The payload is the affected row (the new row for INSERT/UPDATE,
 * the old row for DELETE).
 *
 * Rows that don't deserialize into [T] (e.g. a partial DELETE payload that lacks
 * non-replica-identity columns) are skipped rather than throwing, so one bad
 * event can't tear down the subscription. Use the raw `onPostgresChange` overload
 * if you need to see every payload.
 *
 * ```
 * channel.onPostgresChange<Message>(table = "messages", filter = realtimeFilter { eq("room_id", roomId) }) { msg ->
 *     messages.update { it + msg }
 * }
 * ```
 */
public inline fun <reified T> RealtimeChannelBuilder.onPostgresChange(
    schema: String = "public",
    table: String? = null,
    filter: String? = null,
    event: PostgresChangeEvent = PostgresChangeEvent.ALL,
    crossinline onRow: suspend (T) -> Unit,
): RealtimeChannelBuilder =
    onPostgresChange(schema = schema, table = table, filter = filter, event = event) { json ->
        val row =
            try {
                defaultJson.decodeFromJsonElement<T>(json)
            } catch (e: SerializationException) {
                null
            }
        if (row != null) onRow(row)
    }

/**
 * Subscribes to a channel's `postgres_changes` and decodes each changed row into
 * [T]. A one-call shortcut over [onPostgresChange]; see it for the decoding and
 * skip-on-mismatch semantics.
 */
public suspend inline fun <reified T> RealtimeClient.subscribeToPostgresChanges(
    channel: String,
    schema: String = "public",
    table: String? = null,
    filter: String? = null,
    event: PostgresChangeEvent = PostgresChangeEvent.ALL,
    crossinline onRow: suspend (T) -> Unit,
): RealtimeSubscription =
    subscribe(channel) {
        onPostgresChange<T>(
            schema = schema,
            table = table,
            filter = filter,
            event = event,
            onRow = onRow,
        )
    }
