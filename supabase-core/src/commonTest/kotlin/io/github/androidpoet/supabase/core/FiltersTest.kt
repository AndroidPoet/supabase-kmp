package io.github.androidpoet.supabase.core
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.models.TextSearchType
import io.github.androidpoet.supabase.core.models.filters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiltersTest {
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
    fun test_eq_booleanOverload_producesCorrectParam() {
        val result = filters { eq("is_active", true) }
        assertEquals(listOf("is_active" to "eq.true"), result)
    }

    @Test
    fun test_neq_booleanOverload_producesCorrectParam() {
        val result = filters { neq("archived", false) }
        assertEquals(listOf("archived" to "neq.false"), result)
    }

    @Test
    fun test_is_nullOverload_producesIsNull() {
        val result = filters { `is`("deleted_at", null) }
        assertEquals(listOf("deleted_at" to "is.null"), result)
    }

    @Test
    fun test_is_booleanOverload_producesIsBoolean() {
        val result = filters { `is`("verified", true) }
        assertEquals(listOf("verified" to "is.true"), result)
    }

    // --- Value quoting / injection safety -------------------------------------
    // PostgREST treats comma, parentheses and double-quote as structural in a
    // filter value; an unquoted value containing one would change the query's
    // meaning. These lock the quoting/escaping so filter values can't be used to
    // inject query structure.

    @Test
    fun test_eq_valueWithComma_isDoubleQuoted() {
        assertEquals(listOf("tag" to """eq."a,b""""), filters { eq("tag", "a,b") })
    }

    @Test
    fun test_eq_valueWithParentheses_isDoubleQuoted() {
        assertEquals(listOf("note" to """eq."f(x)""""), filters { eq("note", "f(x)") })
    }

    @Test
    fun test_eq_valueWithDoubleQuote_isEscapedAndQuoted() {
        assertEquals(listOf("name" to """eq."a\"b""""), filters { eq("name", "a\"b") })
    }

    @Test
    fun test_eq_valueWithBackslash_isEscapedAndQuoted() {
        assertEquals(listOf("path" to """eq."a\\b""""), filters { eq("path", "a\\b") })
    }

    @Test
    fun test_eq_emptyValue_isQuoted() {
        assertEquals(listOf("name" to """eq."""""), filters { eq("name", "") })
    }

    @Test
    fun test_eq_plainValue_isNotQuoted() {
        assertEquals(listOf("status" to "eq.active"), filters { eq("status", "active") })
    }

    @Test
    fun test_in_quotesElementsContainingSeparators() {
        assertEquals(
            listOf("tag" to """in.("a,b",plain)"""),
            filters { `in`("tag", listOf("a,b", "plain")) },
        )
    }

    @Test
    fun test_nested_and_within_or_combinesCorrectly() {
        val result =
            filters {
                or {
                    eq("status", "active")
                    and {
                        gte("age", "18")
                        lt("age", "65")
                    }
                }
            }
        assertEquals(listOf("or" to "(status.eq.active,and.(age.gte.18,age.lt.65))"), result)
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

    @Test
    fun test_like_producesCorrectParam() {
        val result = filters { like("name", "%john%") }
        assertEquals(listOf("name" to "like.%john%"), result)
    }

    @Test
    fun test_likeAllOf_producesCorrectParam() {
        val result = filters { likeAllOf("name", listOf("O%", "%n")) }
        assertEquals(listOf("name" to "like(all).{O%,%n}"), result)
    }

    @Test
    fun test_likeAnyOf_producesCorrectParam() {
        val result = filters { likeAnyOf("name", listOf("O%", "P%")) }
        assertEquals(listOf("name" to "like(any).{O%,P%}"), result)
    }

    @Test
    fun test_ilike_producesCorrectParam() {
        val result = filters { ilike("email", "%@EXAMPLE.COM") }
        assertEquals(listOf("email" to "ilike.%@EXAMPLE.COM"), result)
    }

    @Test
    fun test_ilikeAllOf_producesCorrectParam() {
        val result = filters { ilikeAllOf("email", listOf("%@example.com", "%admin%")) }
        assertEquals(listOf("email" to "ilike(all).{%@example.com,%admin%}"), result)
    }

    @Test
    fun test_ilikeAnyOf_producesCorrectParam() {
        val result = filters { ilikeAnyOf("email", listOf("%@example.com", "%@test.com")) }
        assertEquals(listOf("email" to "ilike(any).{%@example.com,%@test.com}"), result)
    }

    @Test
    fun test_in_producesCorrectParam() {
        val result = filters { `in`("id", listOf("1", "2", "3")) }
        assertEquals(listOf("id" to "in.(1,2,3)"), result)
    }

    @Test
    fun test_is_producesCorrectParam() {
        val result = filters { `is`("deleted_at", "null") }
        assertEquals(listOf("deleted_at" to "is.null"), result)
    }

    @Test
    fun test_or_combinesFilters() {
        val result =
            filters {
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
        val result =
            filters {
                not { eq("role", "admin") }
            }
        assertEquals(listOf("role" to "not.eq.admin"), result)
    }

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

    @Test
    fun test_order_withReferencedTable() {
        val result = filters { order("created_at", referencedTable = "profiles") }
        assertEquals(listOf("profiles.order" to "created_at.asc"), result)
    }

    @Test
    fun test_limit_producesCorrectParam() {
        val result = filters { limit(25) }
        assertEquals(listOf("limit" to "25"), result)
    }

    @Test
    fun test_limit_withReferencedTable() {
        val result = filters { limit(5, referencedTable = "profiles") }
        assertEquals(listOf("profiles.limit" to "5"), result)
    }

    @Test
    fun test_range_producesOffsetAndLimit() {
        val result = filters { range(10, 19) }
        assertEquals(2, result.size)
        assertEquals("offset" to "10", result[0])
        assertEquals("limit" to "10", result[1])
    }

    @Test
    fun test_range_withReferencedTable() {
        val result = filters { range(0, 9, referencedTable = "profiles") }
        assertEquals(2, result.size)
        assertEquals("profiles.offset" to "0", result[0])
        assertEquals("profiles.limit" to "10", result[1])
    }

    @Test
    fun test_multipleFilters_accumulate() {
        val result =
            filters {
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

    @Test
    fun test_textSearch_plain() {
        val result = filters { textSearch("body", "hello world") }
        assertEquals(listOf("body" to "plfts.hello world"), result)
    }

    @Test
    fun test_textSearch_withConfig() {
        val result =
            filters {
                textSearch("body", "hello", config = "english", type = TextSearchType.Websearch)
            }
        assertEquals(listOf("body" to "wfts(english).hello"), result)
    }

    @Test
    fun test_filter_rawOperator() {
        val result = filters { filter("col", "eq", "val") }
        assertEquals(listOf("col" to "eq.val"), result)
    }

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

    @Test
    fun test_overlaps_producesCorrectParam() {
        val result = filters { overlaps("tags", "{b,c}") }
        assertEquals(listOf("tags" to "ov.{b,c}"), result)
    }

    @Test
    fun test_match_producesEqParamsForAllValues() {
        val result = filters { match(mapOf("a" to "1", "b" to "2")) }
        assertEquals(2, result.size)
        assertTrue(result.contains("a" to "eq.1"))
        assertTrue(result.contains("b" to "eq.2"))
    }

    @Test
    fun test_not_operatorOverload_producesCorrectParam() {
        val result = filters { not("status", "eq", "archived") }
        assertEquals(listOf("status" to "not.eq.archived"), result)
    }

    @Test
    fun test_strictlyLeft_aliasesRangeLt() {
        val result = filters { strictlyLeft("period", "(1,10)") }
        assertEquals(listOf("period" to "sl.(1,10)"), result)
    }

    @Test
    fun test_strictlyRight_aliasesRangeGt() {
        val result = filters { strictlyRight("period", "(1,10)") }
        assertEquals(listOf("period" to "sr.(1,10)"), result)
    }

    @Test
    fun test_notExtendRight_aliasesRangeLte() {
        val result = filters { notExtendRight("period", "(1,10)") }
        assertEquals(listOf("period" to "nxr.(1,10)"), result)
    }

    @Test
    fun test_notExtendLeft_aliasesRangeGte() {
        val result = filters { notExtendLeft("period", "(1,10)") }
        assertEquals(listOf("period" to "nxl.(1,10)"), result)
    }

    // --- Escaping of scalar operators that previously interpolated raw values ---
    // These lock in that like/ilike/is/textSearch/filter/not(3-arg) quote
    // structural characters so a value can't break out of the filter grammar
    // (e.g. close an enclosing or(...) group). Wildcards and spaces are NOT
    // structural, so normal patterns stay unquoted.

    @Test
    fun test_like_plainPattern_isNotQuoted() {
        assertEquals(listOf("name" to "like.%john%"), filters { like("name", "%john%") })
    }

    @Test
    fun test_like_patternWithComma_isQuoted() {
        assertEquals(listOf("name" to """like."a,b%""""), filters { like("name", "a,b%") })
    }

    @Test
    fun test_like_patternWithParen_isQuoted() {
        assertEquals(listOf("name" to """like."x)y""""), filters { like("name", "x)y") })
    }

    @Test
    fun test_ilike_patternWithComma_isQuoted() {
        assertEquals(listOf("email" to """ilike."a,b""""), filters { ilike("email", "a,b") })
    }

    @Test
    fun test_likeAllOf_quotesElementsContainingSeparators() {
        assertEquals(
            listOf("name" to """like(all).{"a,b",O%}"""),
            filters { likeAllOf("name", listOf("a,b", "O%")) },
        )
    }

    @Test
    fun test_is_stringWithSeparator_isQuoted() {
        assertEquals(listOf("col" to """is."a,b""""), filters { `is`("col", "a,b") })
    }

    @Test
    fun test_textSearch_queryWithComma_isQuoted() {
        assertEquals(listOf("body" to """plfts."a,b""""), filters { textSearch("body", "a,b") })
    }

    @Test
    fun test_textSearch_plainQueryWithSpace_isNotQuoted() {
        assertEquals(listOf("body" to "plfts.hello world"), filters { textSearch("body", "hello world") })
    }

    @Test
    fun test_filter_valueWithParen_isQuoted() {
        assertEquals(listOf("col" to """eq."a)b""""), filters { filter("col", "eq", "a)b") })
    }

    @Test
    fun test_not_operatorOverload_valueWithComma_isQuoted() {
        assertEquals(listOf("col" to """not.eq."a,b""""), filters { not("col", "eq", "a,b") })
    }

    @Test
    fun test_filter_injectionAttemptCannotBreakOutOfGroup() {
        // A value crafted to close an or(...) group and append a clause must be
        // neutralized by quoting rather than altering the query structure.
        val result = filters { or { eq("status", "active)") } }
        assertEquals(listOf("or" to """(status.eq."active)")"""), result)
    }

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
