package io.github.androidpoet.supabase.codegen

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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

    private val generated = SupabaseModelGenerator.generate(fixture, packageName = "com.example.db")

    @Test
    fun generates_a_serializable_data_class_named_after_the_table() {
        assertContains(generated, "package com.example.db")
        assertContains(generated, "@Serializable")
        assertContains(generated, "data class Todos(")
    }

    @Test
    fun required_columns_are_non_null_and_nullable_columns_are_optional() {
        assertContains(generated, "val id: String") // required (uuid → String)
        assertFalse("val id: String?" in generated, "id is required → must be non-null")
        assertContains(generated, "val createdAt: String?") // not required → nullable
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
    fun snake_case_columns_become_camelCase_with_serialName() {
        assertContains(generated, "@SerialName(\"created_at\")")
        assertContains(generated, "val createdAt:")
    }

    @Test
    fun postgres_enums_become_serializable_enum_classes() {
        assertContains(generated, "enum class PriorityLevel")
        assertContains(generated, "@SerialName(\"low\")")
        assertContains(generated, "LOW")
        assertContains(generated, "HIGH")
        assertContains(generated, "val priority: PriorityLevel?")
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
        val out = SupabaseModelGenerator.generate(schema, packageName = "p")
        assertContains(out, "val `class`: String") // hard keyword → backticked
        assertContains(out, "val `1stPlace`: Int?") // digit-leading identifier → backticked
        assertContains(out, "@SerialName(\"1st_place\")")
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
