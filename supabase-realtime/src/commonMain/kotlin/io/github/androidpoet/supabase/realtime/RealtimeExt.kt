package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

public fun RealtimeSubscription.broadcastFlow(
    event: String? = null,
): Flow<JsonObject> =
    asFlow()
        .filter { it is RealtimeEvent.Broadcast && (event == null || it.event == event) }
        .map { (it as RealtimeEvent.Broadcast).payload }

public fun RealtimeSubscription.postgresInsertsFlow(): Flow<JsonObject> =
    asFlow()
        .filter { it is RealtimeEvent.PostgresInsert }
        .map { (it as RealtimeEvent.PostgresInsert).record }

public fun RealtimeSubscription.postgresUpdatesFlow(): Flow<RealtimeEvent.PostgresUpdate> =
    asFlow()
        .filter { it is RealtimeEvent.PostgresUpdate }
        .map { it as RealtimeEvent.PostgresUpdate }

public fun RealtimeSubscription.postgresDeletesFlow(): Flow<JsonObject> =
    asFlow()
        .filter { it is RealtimeEvent.PostgresDelete }
        .map { (it as RealtimeEvent.PostgresDelete).oldRecord }

public fun RealtimeSubscription.presenceSyncFlow(): Flow<PresenceState> =
    asFlow()
        .filter { it is RealtimeEvent.PresenceSync }
        .map { (it as RealtimeEvent.PresenceSync).state }

public fun RealtimeSubscription.presenceJoinFlow(): Flow<RealtimeEvent.PresenceJoin> =
    asFlow()
        .filter { it is RealtimeEvent.PresenceJoin }
        .map { it as RealtimeEvent.PresenceJoin }

public fun RealtimeSubscription.presenceLeaveFlow(): Flow<RealtimeEvent.PresenceLeave> =
    asFlow()
        .filter { it is RealtimeEvent.PresenceLeave }
        .map { it as RealtimeEvent.PresenceLeave }

public fun RealtimeSubscription.systemEventFlow(
    status: String? = null,
): Flow<RealtimeEvent.SystemEvent> =
    asFlow()
        .filter { it is RealtimeEvent.SystemEvent && (status == null || it.status == status) }
        .map { it as RealtimeEvent.SystemEvent }

public fun RealtimeSubscription.statusFlow(): StateFlow<RealtimeSubscription.Status> =
    status

public suspend fun RealtimeSubscription.awaitSubscribed(
    timeoutMs: Long = 10_000,
): RealtimeSubscription.Status =
    withTimeout(timeoutMs) {
        statusFlow().first { it == RealtimeSubscription.Status.SUBSCRIBED || it == RealtimeSubscription.Status.ERROR }
    }

public suspend fun RealtimeSubscription.awaitUnsubscribed(
    timeoutMs: Long = 10_000,
): RealtimeSubscription.Status =
    withTimeout(timeoutMs) {
        statusFlow().first { it == RealtimeSubscription.Status.UNSUBSCRIBED || it == RealtimeSubscription.Status.ERROR }
    }

public inline fun <reified T> RealtimeSubscription.presenceDataFlow(
    json: Json = Json { ignoreUnknownKeys = true },
    ignoreDecodeErrors: Boolean = true,
): Flow<List<T>> =
    presenceSyncFlow().map { state ->
        state.values.mapNotNull { value ->
            try {
                json.decodeFromString<T>(value.toString())
            } catch (_: Throwable) {
                if (ignoreDecodeErrors) null else throw IllegalArgumentException("Failed to decode presence payload: $value")
            }
        }
    }
