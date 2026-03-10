package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.models.TextSearchType
import io.github.androidpoet.supabase.core.models.filters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiltersTest {

    // ── Comparison operators ─────────────────────────────────────────

    @Test
    fun test_eq_producesCorrectParam() {
        val result = filters { eq("status", "active") }

        assertEquals(listOf("status" to "eq.active"), result)
    }

    @Test
    fun test_neq_producesCorrectParam() {
        val result = filters { neq("role", "admin") }

        assertEquals(listOf("role" to "neq.admin"), result)
    }

    @Test
    fun test_gt_producesCorrectParam() {
        val result = filters { gt("age", "18") }

        assertEquals(listOf("age" to "gt.18"), result)
    }

    @Test
    fun test_gte_producesCorrectParam() {
        val result = filters { gte("score", "90") }

        assertEquals(listOf("score" to "gte.90"), result)
    }

    @Test
    fun test_lt_producesCorrectParam() {
        val result = filters { lt("price", "100") }

        assertEquals(listOf("price" to "lt.100"), result)
    }

    @Test
    fun test_lte_producesCorrectParam() {
        val result = filters { lte("quantity", "0") }

        assertEquals(listOf("quantity" to "lte.0"), result)
    }

    // ── Pattern matching ─────────────────────────────────────────────

    @Test
    fun test_like_producesCorrectParam() {
        val result = filters { like("name", "%john%") }

        assertEquals(listOf("name" to "like.%john%"), result)
    }

    @Test
    fun test_ilike_producesCorrectParam() {
        val result = filters { ilike("email", "%@EXAMPLE.COM") }

        assertEquals(listOf("email" to "ilike.%@EXAMPLE.COM"), result)
    }

    // ── in operator ──────────────────────────────────────────────────

    @Test
    fun test_in_producesCorrectParam() {
        val result = filters { `in`("id", listOf("1", "2", "3")) }

        assertEquals(listOf("id" to "in.(1,2,3)"), result)
    }

    // ── is operator ──────────────────────────────────────────────────

    @Test
    fun test_is_producesCorrectParam() {
        val result = filters { `is`("deleted_at", "null") }

        assertEquals(listOf("deleted_at" to "is.null"), result)
    }

    // ── Logical combinators ──────────────────────────────────────────

    @Test
    fun test_or_combinesFilters() {
        val result = filters {
            or {
                eq("status", "active")
                eq("status", "pending")
            }
        }

        assertEquals(1, result.size)
        assertEquals("or", result[0].first)
        assertEquals("(status.eq.active,status.eq.pending)", result[0].second)
    }

    @Test
    fun test_not_negatesFilter() {
        val result = filters {
            not { eq("role", "admin") }
        }

        assertEquals(listOf("role" to "not.eq.admin"), result)
    }

    // ── Ordering ─────────────────────────────────────────────────────

    @Test
    fun test_order_ascending() {
        val result = filters { order("created_at") }

        assertEquals(listOf("order" to "created_at.asc"), result)
    }

    @Test
    fun test_order_descending() {
        val result = filters { order("created_at", ascending = false) }

        assertEquals(listOf("order" to "created_at.desc"), result)
    }

    @Test
    fun test_order_nullsFirst() {
        val result = filters { order("name", nullsFirst = true) }

        assertEquals(listOf("order" to "name.asc.nullsfirst"), result)
    }

    // ── Pagination ───────────────────────────────────────────────────

    @Test
    fun test_limit_producesCorrectParam() {
        val result = filters { limit(25) }

        assertEquals(listOf("limit" to "25"), result)
    }

    @Test
    fun test_range_producesOffsetAndLimit() {
        val result = filters { range(10, 19) }

        assertEquals(2, result.size)
        assertEquals("offset" to "10", result[0])
        assertEquals("limit" to "10", result[1])
    }

    // ── Composite usage ──────────────────────────────────────────────

    @Test
    fun test_multipleFilters_accumulate() {
        val result = filters {
            eq("team", "engineering")
            gte("level", "3")
            order("name")
            limit(50)
        }

        assertEquals(4, result.size)
        assertEquals("team" to "eq.engineering", result[0])
        assertEquals("level" to "gte.3", result[1])
        assertEquals("order" to "name.asc", result[2])
        assertEquals("limit" to "50", result[3])
    }

    // ── Text search ──────────────────────────────────────────────────

    @Test
    fun test_textSearch_plain() {
        val result = filters { textSearch("body", "hello world") }

        assertEquals(listOf("body" to "plfts.hello world"), result)
    }

    @Test
    fun test_textSearch_withConfig() {
        val result = filters {
            textSearch("body", "hello", config = "english", type = TextSearchType.Websearch)
        }

        assertEquals(listOf("body" to "wfts(english).hello"), result)
    }

    // ── Raw filter ───────────────────────────────────────────────────

    @Test
    fun test_filter_rawOperator() {
        val result = filters { filter("col", "eq", "val") }

        assertEquals(listOf("col" to "eq.val"), result)
    }

    // ── Contains / containedBy ───────────────────────────────────────

    @Test
    fun test_contains_producesCorrectParam() {
        val result = filters { contains("tags", "{a,b}") }

        assertEquals(listOf("tags" to "cs.{a,b}"), result)
    }

    @Test
    fun test_containedBy_producesCorrectParam() {
        val result = filters { containedBy("tags", "{a,b,c}") }

        assertEquals(listOf("tags" to "cd.{a,b,c}"), result)
    }

    // ── FilterBuilder direct usage ───────────────────────────────────

    @Test
    fun test_filterBuilder_buildReturnsImmutableCopy() {
        val builder = FilterBuilder()
        builder.eq("a", "1")
        val first = builder.build()

        builder.eq("b", "2")
        val second = builder.build()

        assertEquals(1, first.size)
        assertEquals(2, second.size)
    }
}
