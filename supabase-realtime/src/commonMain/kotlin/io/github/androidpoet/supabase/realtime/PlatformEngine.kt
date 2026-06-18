package io.github.androidpoet.supabase.realtime

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * The Ktor engine that backs the realtime WebSocket connection. Each target supplies a
 * concrete engine that supports WebSockets (OkHttp on Android/JVM, Darwin on Apple, CIO on
 * Linux/Windows, Js on Wasm) so [createRealtimeClient] works without the caller wiring one.
 */
internal expect fun platformEngine(): HttpClientEngineFactory<*>
