package io.github.androidpoet.supabase.realtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RealtimeFilterTest {
    @Test
    fun test_eq_string_buildsWireFilter() {
        assertEquals("room_id=eq.abc", realtimeFilter { eq("room_id", "abc") })
    }

    @Test
    fun test_eq_number_buildsWireFilter() {
        assertEquals("room_id=eq.42", realtimeFilter { eq("room_id", 42) })
    }

    @Test
    fun test_eq_boolean_buildsWireFilter() {
        assertEquals("is_active=eq.true", realtimeFilter { eq("is_active", true) })
    }

    @Test
    fun test_comparisonOperators_buildWireFilters() {
        assertEquals("age=gt.18", realtimeFilter { gt("age", 18) })
        assertEquals("age=gte.18", realtimeFilter { gte("age", 18) })
        assertEquals("age=lt.65", realtimeFilter { lt("age", 65) })
        assertEquals("age=lte.65", realtimeFilter { lte("age", 65) })
        assertEquals("role=neq.admin", realtimeFilter { neq("role", "admin") })
    }

    @Test
    fun test_isIn_buildsParenthesizedList() {
        assertEquals("status=in.(open,pending)", realtimeFilter { isIn("status", listOf("open", "pending")) })
    }

    @Test
    fun test_noOperator_returnsNull() {
        assertNull(realtimeFilter { })
    }

    @Test
    fun test_blankColumn_throws() {
        assertFailsWith<IllegalArgumentException> { realtimeFilter { eq("", "x") } }
    }

    @Test
    fun test_secondFilter_throws() {
        assertFailsWith<IllegalStateException> {
            realtimeFilter {
                eq("room_id", "a")
                eq("user_id", "b")
            }
        }
    }
}
