package io.github.androidpoet.supabase.codegen

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseModelGeneratorTest {
    // A representative slice of a REAL PostgREST OpenAPI document. Note the integer formats
    // are "int32"/"int64" (what PostgREST actually emits — it normalises integer/bigint via
    // toSwaggerFormat), and array `items` carry only a coarse `type` with no `format`.
    private val fixture =
        """
        {
          "definitions": {
            "todos": {
              "required": ["id", "title", "done", "rank"],
              "properties": {
                "id":         { "format": "uuid", "type": "string", "description": "Note:\nThis is a Primary Key.<pk/>" },
                "title":      { "format": "text", "type": "string" },
                "done":       { "format": "boolean", "type": "boolean" },
                "rank":       { "format": "int32", "type": "integer" },
                "priority":   { "format": "public.priority_level", "type": "string", "enum": ["low", "high"] },
                "created_at": { "format": "timestamp with time zone", "type": "string" },
                "score":      { "format": "int64", "type": "integer" },
                "metadata":   { "format": "jsonb" },
                "tags":       { "type": "array", "format": "text[]", "items": { "type": "string" } },
                "ratings":    { "type": "array", "format": "numeric[]", "items": { "type": "number" } }
              }
            }
          }
        }
        """.trimIndent()

    // All generated files concatenated, for the content-level assertions below.
    private val generated = source(fixture)

    private fun source(spec: String, packageName: String = "com.example.db"): String =
        SupabaseModelGenerator.generate(spec, packageName).joinToString("\n") { it.contents }

    @Test
    fun generates_a_serializable_data_class_named_after_the_table() {
        assertContains(generated, "package com.example.db.tables")
        assertContains(generated, "@Serializable")
        assertContains(generated, "data class Todos(")
    }

    @Test
    fun emits_one_file_per_table_and_per_enum_in_typed_folders() {
        val paths = SupabaseModelGenerator.generate(fixture, "com.example.db").map { it.relativePath }
        assertContains(paths, "com/example/db/tables/Todos.kt")
        assertContains(paths, "com/example/db/enums/PriorityLevel.kt")
    }

    @Test
    fun every_generated_file_lives_under_a_declared_owned_subpackage() {
        // The writers delete `generatedSubpackages` before each run to clear stale output, so every
        // file MUST live under one of them — otherwise a dropped table/enum would orphan a file the
        // cleanup never touches. This guards that invariant if a new model kind is ever added.
        val owned = SupabaseModelGenerator.generatedSubpackages.map { "com/example/db/$it/" }
        val files = SupabaseModelGenerator.generate(fixture, "com.example.db")
        for (file in files) {
            assertTrue(
                owned.any { file.relativePath.startsWith(it) },
                "generated file ${file.relativePath} is outside the cleaned subpackages $owned",
            )
        }
    }

    @Test
    fun required_columns_are_non_null_and_nullable_columns_are_optional() {
        assertContains(generated, "val id: String") // required (uuid → String)
        assertFalse("val id: String?" in generated, "id is required → must be non-null")
        assertContains(generated, "val createdAt: String?") // not required → nullable
    }

    @Test
    fun nullable_columns_default_to_null_so_partial_selects_decode() {
        // kotlinx.serialization treats a `T?` WITHOUT a default as REQUIRED (the key must
        // be present, even if null), so a `select=...` that omits the column throws
        // MissingFieldException at runtime. Nullable columns must default to null to be
        // truly optional; required columns must NOT, so the row's presence is enforced.
        assertContains(generated, "val createdAt: String? = null")
        assertContains(generated, "val score: Long? = null")
        assertContains(generated, "val metadata: JsonElement? = null")
        assertContains(generated, "val priority: PriorityLevel? = null")
        // Required columns stay non-default.
        assertFalse("val id: String = null" in generated, "required id must not be optional")
        assertFalse("val id: String? = null" in generated, "required id must stay non-null")
    }

    @Test
    fun maps_real_postgrest_integer_formats_to_int_and_long() {
        assertContains(generated, "val rank: Int") // int32 → Int
        assertFalse("val rank: Int?" in generated, "rank is required → must be non-null")
        assertContains(generated, "val score: Long?") // int64 → Long
    }

    @Test
    fun maps_postgres_types_to_multiplatform_kotlin_types() {
        assertContains(generated, "val done: Boolean")
        assertContains(generated, "val metadata: JsonElement?") // jsonb (no `type`) → JsonElement
        assertContains(generated, "val tags: List<String>?") // text[] → List<String>
        assertContains(generated, "val ratings: List<Double>?") // numeric[] (element type "number") → List<Double>
    }

    @Test
    fun emits_column_tokens_in_a_companion_for_the_typed_filter_dsl() {
        // Each data class carries a `companion object` of Column<T> tokens so `Todos.done`
        // works with the typed `where { }` DSL without anyone hand-writing the tokens.
        assertContains(generated, "import io.github.androidpoet.supabase.core.models.Column")
        assertContains(generated, "companion object {")
        assertContains(generated, "val done: Column<Boolean> = Column(\"done\")")
        // Wire name (snake_case) is preserved in the token; the property is camelCase.
        assertContains(generated, "val createdAt: Column<String> = Column(\"created_at\")")
        // Enum-backed and collection columns keep their element types.
        assertContains(generated, "val priority: Column<PriorityLevel> = Column(\"priority\")")
        assertContains(generated, "val tags: Column<List<String>> = Column(\"tags\")")
    }

    @Test
    fun column_tokens_drop_nullability_so_operators_take_a_bare_value() {
        // `Column<T>.eq(value: T)` takes a bare T; isNull()/isNotNull() cover the null cases.
        // A nullable column's token must therefore be `Column<Long>`, not `Column<Long?>`.
        assertContains(generated, "val score: Column<Long> = Column(\"score\")")
        assertFalse("Column<Long?>" in generated, "token type must drop nullability")
        assertFalse("Column<String?>" in generated, "token type must drop nullability")
    }

    @Test
    fun maps_money_to_string_but_numeric_to_double() {
        // Postgres `money` (CASHOID) is NOT a JSONTYPE_NUMERIC type, so PostgREST emits it as a
        // locale-formatted string (e.g. "$1,234.56") — decoding it into Double throws on every row.
        // It must map to String. `numeric`/`decimal` ARE emitted as JSON numbers, so they stay Double.
        val spec =
            """
            {
              "definitions": {
                "invoices": {
                  "required": ["id"],
                  "properties": {
                    "id":      { "format": "uuid", "type": "string" },
                    "price":   { "format": "money", "type": "string" },
                    "tax":     { "format": "numeric", "type": "number" },
                    "total":   { "format": "decimal", "type": "number" }
                  }
                }
              }
            }
            """.trimIndent()

        val generated = source(spec)

        assertContains(generated, "val price: String?") // money → String (would throw as Double)
        assertContains(generated, "val tax: Double?") // numeric → Double (JSON number)
        assertContains(generated, "val total: Double?") // decimal → Double (JSON number)
    }

    @Test
    fun snake_case_columns_become_camelCase_with_serialName() {
        assertContains(generated, "@SerialName(\"created_at\")")
        assertContains(generated, "val createdAt:")
    }

    @Test
    fun postgres_enums_become_serializable_enum_classes_in_their_own_package() {
        assertContains(generated, "enum class PriorityLevel")
        assertContains(generated, "@SerialName(\"low\")")
        assertContains(generated, "LOW")
        assertContains(generated, "HIGH")
        assertContains(generated, "val priority: PriorityLevel?")
        // Enums live in their own `.enums` package; the table file imports them from there.
        assertContains(generated, "package com.example.db.enums")
        assertContains(generated, "import com.example.db.enums.PriorityLevel")
        // Never an empty-package import (`import PriorityLevel`), which Kotlin forbids and
        // would make the generated file uncompilable. Guard against that regression.
        assertFalse(
            "import PriorityLevel\n" in generated,
            "enum must not be imported from the default package (uncompilable)",
        )
    }

    @Test
    fun imports_only_multiplatform_serialization_symbols() {
        assertContains(generated, "import kotlinx.serialization.Serializable")
        assertContains(generated, "import kotlinx.serialization.SerialName")
        assertContains(generated, "import kotlinx.serialization.json.JsonElement")
        // No JVM-only imports leak into the generated (common) source.
        assertFalse("import java." in generated, "generated models must stay multiplatform-common")
    }

    @Test
    fun escapes_kotlin_keywords_and_digit_leading_column_names() {
        val schema =
            """
            {
              "definitions": {
                "weird": {
                  "required": ["class"],
                  "properties": {
                    "class":     { "format": "text", "type": "string" },
                    "1st_place": { "format": "int32", "type": "integer" }
                  }
                }
              }
            }
            """.trimIndent()
        val out = source(schema, "p")
        assertContains(out, "val `class`: String") // hard keyword → backticked
        assertContains(out, "val `1stPlace`: Int?") // digit-leading identifier → backticked
        assertContains(out, "@SerialName(\"1st_place\")")
    }

    @Test
    fun fails_fast_when_two_columns_collide_on_one_kotlin_property() {
        // user_id and userId both normalise to the Kotlin property `userId`, which would emit a
        // data class with two identical properties that doesn't compile. Fail fast instead.
        val schema =
            """
            {
              "definitions": {
                "events": {
                  "required": [],
                  "properties": {
                    "user_id": { "format": "int64", "type": "integer" },
                    "userId":  { "format": "int64", "type": "integer" }
                  }
                }
              }
            }
            """.trimIndent()
        val error =
            assertFailsWith<IllegalStateException> {
                SupabaseModelGenerator.generate(schema, packageName = "p")
            }
        assertContains(error.message ?: "", "userId")
    }

    @Test
    fun fails_fast_when_two_enum_values_collide_on_one_kotlin_constant() {
        // Postgres enum labels are case-sensitive and allow spaces, so `active` and `Active`
        // are distinct labels that both normalise to the Kotlin constant ACTIVE — which would
        // emit a duplicate enum constant that doesn't compile. Fail fast instead.
        val schema =
            """
            {
              "definitions": {
                "items": {
                  "required": [],
                  "properties": {
                    "status": { "format": "public.status", "type": "string", "enum": ["active", "Active"] }
                  }
                }
              }
            }
            """.trimIndent()
        val error =
            assertFailsWith<IllegalStateException> {
                SupabaseModelGenerator.generate(schema, packageName = "p")
            }
        assertContains(error.message ?: "", "ACTIVE")
    }

    @Test
    fun a_table_and_an_enum_with_the_same_name_no_longer_collide() {
        // Table `order_status` → `tables/OrderStatus.kt`; a column whose Postgres enum type
        // is `order_status` → `enums/OrderStatus.kt`. In the single-file layout these were a
        // fatal redeclaration; split into typed sub-packages they coexist cleanly.
        val schema =
            """
            {
              "definitions": {
                "order_status": {
                  "required": [],
                  "properties": { "id": { "format": "uuid", "type": "string" } }
                },
                "orders": {
                  "required": [],
                  "properties": {
                    "status": { "format": "public.order_status", "type": "string", "enum": ["open", "closed"] }
                  }
                }
              }
            }
            """.trimIndent()
        val paths = SupabaseModelGenerator.generate(schema, "p").map { it.relativePath }
        assertContains(paths, "p/tables/OrderStatus.kt")
        assertContains(paths, "p/enums/OrderStatus.kt")
    }

    @Test
    fun fails_fast_when_two_tables_collide_on_one_kotlin_name() {
        val schema =
            """
            {
              "definitions": {
                "user_profile":  { "required": [], "properties": { "id": { "format": "uuid", "type": "string" } } },
                "user__profile": { "required": [], "properties": { "id": { "format": "uuid", "type": "string" } } }
              }
            }
            """.trimIndent()
        val error =
            assertFailsWith<IllegalStateException> {
                SupabaseModelGenerator.generate(schema, packageName = "p")
            }
        assertContains(error.message ?: "", "UserProfile")
    }
}
