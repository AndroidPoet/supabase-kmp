package io.github.androidpoet.supabase.e2e

import io.github.androidpoet.supabase.realtime.binaryBroadcastFlow
import io.github.androidpoet.supabase.realtime.createRealtimeClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises binary broadcast end to end against the REAL hosted Realtime server:
 * subscribe to a throwaway channel with self-echo on, send raw bytes via
 * [broadcastBinary], and assert the exact same bytes come back through
 * [binaryBroadcastFlow] — proving the kind-3 encode and kind-4 decode match the
 * server's wire format.
 *
 * Requires a Realtime server that supports binary payloads (>= 2.103.2). Configure
 * via `SUPABASE_E2E_URL` / `SUPABASE_E2E_ANON_KEY` (see [E2e]); skipped otherwise.
 * Uses runBlocking (real clock) rather than runTest so the network timeout is real.
 */
class RealtimeBinaryE2eTest {
    @Test
    fun test_realtime_binaryBroadcast_roundTripsRawBytes() {
        // Needs a binary-capable hosted Realtime server (>= 2.103.2); the local CI
        // lane has no hosted config. Skip (not fail) when it isn't configured.
        val config = E2e.configOrNull()
        assumeTrue(
            "requires hosted E2E config (SUPABASE_E2E_URL/ANON_KEY) — skipped without it",
            config?.serviceKey != null,
        )
        runBlocking {
            val client = E2e.anonClient(config!!)
            val realtime = createRealtimeClient(client)

            val channelName = E2e.artifact("bin-bcast")
            val event = "frame"
            // A non-textual, signed-byte payload that would not survive a JSON/UTF-8 round-trip.
            val payload = byteArrayOf(0, 1, 2, 3, 42, -7, -128, 127, 99, 0)

            val subscription =
                realtime
                    .channel(channelName)
                    .configureBroadcast(receiveOwnBroadcasts = true)
                    .subscribeWithResult()
                    .unwrap("subscribe")

            try {
                val received = CompletableDeferred<ByteArray>()
                val collector =
                    launch {
                        subscription.binaryBroadcastFlow(event).collect { bytes ->
                            received.complete(bytes)
                        }
                    }
                // Let the collector attach (SharedFlow has no replay) before sending.
                delay(500)
                subscription.broadcastBinary(event, payload)

                val got = withTimeout(15_000) { received.await() }
                assertEquals(payload.toList(), got.toList(), "binary payload must round-trip byte-for-byte")
                collector.cancel()
            } finally {
                subscription.unsubscribe()
                realtime.disconnect()
            }
        }
    }
}
