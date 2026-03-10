package io.github.androidpoet.supabase.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * A live subscription to a Supabase Realtime channel.
 *
 * Provides a reactive [Flow] of [RealtimeEvent]s and methods for
 * sending data back through the channel (broadcast, presence tracking).
 */
public interface RealtimeSubscription {

    /** The channel name this subscription is bound to. */
    public val channel: String

    /** Returns a cold [Flow] that emits [RealtimeEvent]s for this channel. */
    public fun asFlow(): Flow<RealtimeEvent>

    /** Leaves the channel and stops receiving events. */
    public suspend fun unsubscribe()

    /** Sends a broadcast [event] with the given [payload] to all channel subscribers. */
    public suspend fun broadcast(event: String, payload: JsonObject)

    /** Tracks the local client's [state] in the channel's presence set. */
    public suspend fun track(state: JsonObject)

    /** Removes the local client from the channel's presence set. */
    public suspend fun untrack()
}
