package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.models.Column
import io.github.androidpoet.supabase.core.models.Nulls
import io.github.androidpoet.supabase.core.models.Order
import io.github.androidpoet.supabase.core.models.query
import io.github.androidpoet.supabase.core.models.where
import kotlin.test.Test
import kotlin.test.assertEquals

private val id = Column<Long>("id")
private val age = Column<Int>("age")
private val name = Column<String>("name")
private val status = Column<String>("status")
private val active = Column<Boolean>("active")
private val deletedAt = Column<String>("deleted_at")
private val tags = Column<List<String>>("tags")

class FiltersTest {
    @Test
    fun comparisonsRenderExpectedWireForms() {
        assertEquals(listOf("id" to "eq.7"), where { id eq 7 })
        assertEquals(listOf("id" to "neq.7"), where { id neq 7 })
        assertEquals(listOf("age" to "gt.18"), where { age greater 18 })
        assertEquals(listOf("age" to "gte.18"), where { age greaterEq 18 })
        assertEquals(listOf("age" to "lt.65"), where { age less 65 })
        assertEquals(listOf("age" to "lte.65"), where { age lessEq 65 })
    }

    @Test
    fun stringValuesAreQuotedOnlyWhenStructural() {
        assertEquals(listOf("name" to "eq.Ann"), where { name eq "Ann" })
        assertEquals(listOf("name" to "eq.\"a,b\""), where { name eq "a,b" })
        assertEquals(listOf("active" to "eq.true"), where { active eq true })
    }

    @Test
    fun withinExpandsToTwoBounds() {
        assertEquals(listOf("age" to "gte.18", "age" to "lte.65"), where { age within 18..65 })
    }

    @Test
    fun nullAndBooleanIs() {
        assertEquals(listOf("deleted_at" to "is.null"), where { deletedAt.isNull() })
        assertEquals(listOf("deleted_at" to "not.is.null"), where { deletedAt.isNotNull() })
        assertEquals(listOf("active" to "is.false"), where { active isExactly false })
    }

    @Test
    fun inListAndLike() {
        assertEquals(listOf("status" to "in.(a,b,c)"), where { status inList listOf("a", "b", "c") })
        assertEquals(listOf("id" to "in.(1,2,3)"), where { id inList listOf(1L, 2L, 3L) })
        assertEquals(listOf("name" to "like.A%"), where { name like "A%" })
    }

    @Test
    fun multipleStatementsAreImplicitlyAnded() {
        assertEquals(
            listOf("age" to "gte.18", "status" to "eq.active"),
            where {
                age greaterEq 18
                status eq "active"
            },
        )
    }

    @Test
    fun orGroupRendersGroupedForm() {
        assertEquals(
            listOf("or" to "(age.lt.18,age.gt.65)"),
            where {
                or {
                    age less 18
                    age greater 65
                }
            },
        )
    }

    @Test
    fun explicitAndGroupNestedInsideOr() {
        assertEquals(
            listOf("or" to "(status.eq.active,and(age.gte.18,age.lt.65))"),
            where {
                or {
                    status eq "active"
                    and {
                        age greaterEq 18
                        age less 65
                    }
                }
            },
        )
    }

    @Test
    fun notNegatesLeafAndGroup() {
        assertEquals(listOf("age" to "not.gt.18"), where { not { age greater 18 } })
        assertEquals(
            listOf("not.and" to "(age.gte.18,age.lt.65)"),
            where {
                not {
                    and {
                        age greaterEq 18
                        age less 65
                    }
                }
            },
        )
    }

    @Test
    fun referencedTableScopesGroup() {
        assertEquals(
            listOf("authors.or" to "(name.eq.x,name.eq.y)"),
            where {
                or(referencedTable = "authors") {
                    name eq "x"
                    name eq "y"
                }
            },
        )
    }

    @Test
    fun arrayContains() {
        assertEquals(listOf("tags" to "cs.{a,b}"), where { tags contains listOf("a", "b") })
    }

    @Test
    fun queryAddsModifiersAfterFilters() {
        assertEquals(
            listOf("active" to "eq.true", "order" to "created_at.desc", "limit" to "20"),
            query {
                where { active eq true }
                orderBy(Column<String>("created_at"), Order.DESC)
                limit(20)
            },
        )
    }

    @Test
    fun multipleOrderByCoalesceIntoOneParam() {
        assertEquals(
            listOf("order" to "a.asc,b.desc.nullslast"),
            query {
                orderBy(Column<Int>("a"))
                orderBy(Column<Int>("b"), Order.DESC, Nulls.LAST)
            },
        )
    }

    @Test
    fun limitOverwritesInsteadOfDuplicating() {
        // The old builder silently emitted both limit=10 and limit=5; now last wins
        // (range overwrites the earlier limit in place rather than appending a dup).
        val params =
            query {
                limit(10)
                range(0, 4)
            }
        assertEquals(1, params.count { it.first == "limit" })
        assertEquals(mapOf("limit" to "5", "offset" to "0"), params.toMap())
    }
}
