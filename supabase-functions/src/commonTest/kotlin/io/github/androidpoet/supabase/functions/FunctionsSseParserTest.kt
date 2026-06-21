package io.github.androidpoet.supabase.functions

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionsSseParserTest {
    @Test
    fun test_lastEventId_persistsAcrossEvents() =
        runTest {
            val events = parseServerSentEvents(flowOf("id: 5", "data: a", "", "data: b", "")).toList()

            assertEquals(2, events.size)
            assertEquals("5", events[0].id)
            assertEquals("a", events[0].data)
            // The second event omits id: — per the SSE spec / EventSource.lastEventId it must
            // inherit the persisted last-event-id, not report null.
            assertEquals("5", events[1].id)
            assertEquals("b", events[1].data)
        }

    @Test
    fun test_keepaliveAfterIdEvent_doesNotEmitSpuriousEvent() =
        runTest {
            // A comment keep-alive followed by a blank line, after an id-bearing event, must NOT
            // dispatch an empty {id, data=null} event now that the id persists across events.
            val events = parseServerSentEvents(flowOf("id: 7", "data: x", "", ": keep-alive", "")).toList()

            assertEquals(1, events.size)
            assertEquals("x", events[0].data)
        }

    @Test
    fun test_eventOnlyBlock_isSurfaced_forTerminalSignals() =
        runTest {
            // Streaming functions signal completion with an event:-only block (no data).
            val events = parseServerSentEvents(flowOf("event: done", "")).toList()

            assertEquals(1, events.size)
            assertEquals("done", events[0].event)
            assertEquals(null, events[0].data)
        }

    @Test
    fun test_idOnlyBlock_doesNotEmit() =
        runTest {
            // A block carrying only id: (no data, no event type) does not dispatch on its own.
            val events = parseServerSentEvents(flowOf("id: 9", "")).toList()

            assertEquals(0, events.size)
        }

    @Test
    fun test_multipleDataLines_joinedWithNewline() =
        runTest {
            val events = parseServerSentEvents(flowOf("data: line1", "data: line2", "")).toList()

            assertEquals(1, events.size)
            assertEquals("line1\nline2", events[0].data)
        }

    @Test
    fun test_trailingUnterminatedEvent_isFlushed() =
        runTest {
            val events = parseServerSentEvents(flowOf("data: final")).toList()

            assertEquals(1, events.size)
            assertEquals("final", events[0].data)
        }

    @Test
    fun test_crlfLineEndings_areTolerated() =
        runTest {
            // A lone trailing \r (CRLF stream where the reader stripped only \n) must be trimmed.
            val events = parseServerSentEvents(flowOf("data: hi\r", "\r")).toList()

            assertEquals(1, events.size)
            assertEquals("hi", events[0].data)
        }
}
