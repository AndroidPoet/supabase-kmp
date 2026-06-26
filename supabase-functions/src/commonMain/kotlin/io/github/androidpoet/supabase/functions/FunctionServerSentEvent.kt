package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.client.defaultJson

/**
 * One Server-Sent Event emitted by a streaming Edge Function, as produced by
 * [FunctionsClient.invokeSse].
 *
 * Fields mirror the SSE wire format: [id] (`id:`), [event] (`event:`, defaulting
 * to `"message"` on the wire when omitted) and [data] (`data:`; multiple `data:`
 * lines in one event are joined with `\n`). Keep-alive comment lines (`:`) are
 * dropped by the parser and never surface as events.
 *
 * [id] carries the *last-event-id*: it persists across events, so an event that
 * omits `id:` reports the most recently seen id (matching the browser
 * `EventSource.lastEventId`), and is `null` only until the server first sends one.
 */
public data class FunctionServerSentEvent(
    public val id: String? = null,
    public val event: String? = null,
    public val data: String? = null,
) {
    /**
     * Deserializes [data] into [T] using the Functions serializer.
     *
     * Returns `null` when this event carries no [data] (e.g. an event with only
     * an `id:`/`event:` line). Throws if [data] is present but not valid JSON for
     * [T] — wrap the call site if you need that as a value.
     */
    public inline fun <reified T> decodeAs(): T? = data?.let { defaultJson.decodeFromString<T>(it) }
}
