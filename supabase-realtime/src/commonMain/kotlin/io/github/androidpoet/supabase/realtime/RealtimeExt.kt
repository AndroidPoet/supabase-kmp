package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Narrows [asFlow] to broadcast payloads, optionally only those whose event name
 * equals [event] (all broadcasts when `null`). Yields each message's [JsonObject]
 * payload — the typed view over [RealtimeEvent.Broadcast].
 */
public fun RealtimeSubscription.broadcastFlow(
    event: String? = null,
): Flow<JsonObject> =
    asFlow()
        .filter { it is RealtimeEvent.Broadcast && (event == null || it.event == event) }
        .map { (it as RealtimeEvent.Broadcast).payload }

/**
 * Narrows [asFlow] to binary broadcasts, optionally only those whose event name
 * equals [event] (all when `null`). Yields each message's raw [ByteArray] payload —
 * the typed view over [RealtimeEvent.BinaryBroadcast]. Pair with
 * [RealtimeSubscription.broadcastBinary] for sending.
 */
public fun RealtimeSubscription.binaryBroadcastFlow(
    event: String? = null,
): Flow<ByteArray> =
    asFlow()
        .filter { it is RealtimeEvent.BinaryBroadcast && (event == null || it.event == event) }
        .map { (it as RealtimeEvent.BinaryBroadcast).payload }

/** Narrows [asFlow] to `postgres_changes` INSERTs, yielding each inserted row
 * ([RealtimeEvent.PostgresInsert.record]). */
public fun RealtimeSubscription.postgresInsertsFlow(): Flow<JsonObject> =
    asFlow()
        .filter { it is RealtimeEvent.PostgresInsert }
        .map { (it as RealtimeEvent.PostgresInsert).record }

/** Narrows [asFlow] to `postgres_changes` UPDATEs as [RealtimeEvent.PostgresUpdate]
 * (carrying both new and old row, when replica identity provides the old one). */
public fun RealtimeSubscription.postgresUpdatesFlow(): Flow<RealtimeEvent.PostgresUpdate> =
    asFlow()
        .filter { it is RealtimeEvent.PostgresUpdate }
        .map { it as RealtimeEvent.PostgresUpdate }

/** Narrows [asFlow] to `postgres_changes` DELETEs, yielding the deleted row
 * ([RealtimeEvent.PostgresDelete.oldRecord]). */
public fun RealtimeSubscription.postgresDeletesFlow(): Flow<JsonObject> =
    asFlow()
        .filter { it is RealtimeEvent.PostgresDelete }
        .map { (it as RealtimeEvent.PostgresDelete).oldRecord }

/** Narrows [asFlow] to presence syncs, yielding the full cumulative
 * [PresenceState] on each `presence_state`/`presence_diff`. */
public fun RealtimeSubscription.presenceSyncFlow(): Flow<PresenceState> =
    asFlow()
        .filter { it is RealtimeEvent.PresenceSync }
        .map { (it as RealtimeEvent.PresenceSync).state }

/** Narrows [asFlow] to presence join events ([RealtimeEvent.PresenceJoin]), one
 * per member that joins the channel. */
public fun RealtimeSubscription.presenceJoinFlow(): Flow<RealtimeEvent.PresenceJoin> =
    asFlow()
        .filter { it is RealtimeEvent.PresenceJoin }
        .map { it as RealtimeEvent.PresenceJoin }

/** Narrows [asFlow] to presence leave events ([RealtimeEvent.PresenceLeave]), one
 * per member that leaves the channel. */
public fun RealtimeSubscription.presenceLeaveFlow(): Flow<RealtimeEvent.PresenceLeave> =
    asFlow()
        .filter { it is RealtimeEvent.PresenceLeave }
        .map { it as RealtimeEvent.PresenceLeave }

/** Narrows [asFlow] to channel system events ([RealtimeEvent.SystemEvent]),
 * optionally only those with the given [status] (all when `null`). */
public fun RealtimeSubscription.systemEventFlow(
    status: String? = null,
): Flow<RealtimeEvent.SystemEvent> =
    asFlow()
        .filter { it is RealtimeEvent.SystemEvent && (status == null || it.status == status) }
        .map { it as RealtimeEvent.SystemEvent }

/**
 * Suspends until this subscription reaches `SUBSCRIBED` (or `ERROR`) and returns
 * that status, throwing [kotlinx.coroutines.TimeoutCancellationException] after
 * [timeoutMs]. Turns the fire-and-forget [RealtimeChannelBuilder.subscribe] into
 * an await; [RealtimeChannelBuilder.subscribeWithResult] is the Result-first form.
 */
public suspend fun RealtimeSubscription.awaitSubscribed(
    timeoutMs: Long = 10_000,
): RealtimeSubscription.Status =
    withTimeout(timeoutMs) {
        status.first { it == RealtimeSubscription.Status.SUBSCRIBED || it == RealtimeSubscription.Status.ERROR }
    }

/**
 * Suspends until this subscription reaches `UNSUBSCRIBED` (or `ERROR`) and returns
 * that status, throwing [kotlinx.coroutines.TimeoutCancellationException] after
 * [timeoutMs]. Use to confirm a leave completed after [RealtimeSubscription.unsubscribe].
 */
public suspend fun RealtimeSubscription.awaitUnsubscribed(
    timeoutMs: Long = 10_000,
): RealtimeSubscription.Status =
    withTimeout(timeoutMs) {
        status.first { it == RealtimeSubscription.Status.UNSUBSCRIBED || it == RealtimeSubscription.Status.ERROR }
    }

/**
 * Like [presenceSyncFlow] but decodes each member's presence payload into [T],
 * emitting the list of decoded members on every sync.
 *
 * With [ignoreDecodeErrors] (default `true`) members whose payload doesn't fit
 * [T] are skipped so one malformed member can't break the flow; set it `false` to
 * fail fast with [IllegalArgumentException]. [CancellationException] is always
 * rethrown so collection stays cancellable.
 *
 * @param json the serializer used to decode each member payload.
 */
public inline fun <reified T> RealtimeSubscription.presenceDataFlow(
    json: Json = Json { ignoreUnknownKeys = true },
    ignoreDecodeErrors: Boolean = true,
): Flow<List<T>> =
    presenceSyncFlow().map { state ->
        state.values.mapNotNull { value ->
            try {
                json.decodeFromString<T>(value.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (ignoreDecodeErrors) null else throw IllegalArgumentException("Failed to decode presence payload: $value")
            }
        }
    }
