package io.github.androidpoet.supabase.codegen

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseSqlDelightAdapterGeneratorTest {
    private val fixture =
        """
        {
          "definitions": {
            "todos": {
              "required": ["id", "title", "done", "rank", "score"],
              "properties": {
                "id":         { "format": "uuid", "type": "string", "description": "Note:\nThis is a Primary Key.<pk/>" },
                "title":      { "format": "text", "type": "string" },
                "done":       { "format": "boolean", "type": "boolean" },
                "rank":       { "format": "int32", "type": "integer" },
                "score":      { "format": "int64", "type": "integer" },
                "price":      { "format": "numeric", "type": "number" },
                "settings":   { "format": "jsonb", "type": "object" },
                "tags":       { "type": "array", "items": { "type": "string" } },
                "created_at": { "format": "timestamp with time zone", "type": "string" }
              }
            }
          }
        }
        """.trimIndent()

    private val file = SupabaseSqlDelightAdapterGenerator.generate(fixture, packageName = "com.example.db")
    private val src = file.contents

    @Test
    fun emits_one_adapters_file_under_the_package_path() {
        assertEquals("com/example/db/SupabaseAdapters.kt", file.relativePath)
        assertContains(src, "package com.example.db")
        assertContains(src, "public fun supabaseAdapters(driver: SqlDriver): Map<String, TableAdapter>")
    }

    @Test
    fun imports_the_offline_sync_runtime() {
        assertContains(src, "import io.github.androidpoet.offlinesync.TableAdapter")
        assertContains(src, "import io.github.androidpoet.offlinesync.store.Column")
        assertContains(src, "import io.github.androidpoet.offlinesync.store.ColumnKind")
        assertContains(src, "import io.github.androidpoet.offlinesync.store.SqlDelightTableAdapter")
    }

    @Test
    fun wires_the_table_with_its_primary_key() {
        assertContains(src, "\"todos\" to SqlDelightTableAdapter(")
        assertContains(src, "table = \"todos\"")
        assertContains(src, "pk = \"id\"")
    }

    @Test
    fun classifies_every_column_kind() {
        assertContains(src, "Column(\"title\", ColumnKind.TEXT, nullable = false)") // text + required
        assertContains(src, "Column(\"done\", ColumnKind.BOOL, nullable = false)") // boolean -> BOOL, not INTEGER
        assertContains(src, "Column(\"rank\", ColumnKind.INTEGER, nullable = false)") // int32
        assertContains(src, "Column(\"score\", ColumnKind.INTEGER, nullable = false)") // int64
        assertContains(src, "Column(\"price\", ColumnKind.REAL, nullable = true)") // numeric + not required -> nullable
        assertContains(src, "Column(\"settings\", ColumnKind.JSON, nullable = true)") // jsonb -> JSON
        assertContains(src, "Column(\"tags\", ColumnKind.JSON, nullable = true)") // array -> JSON
        assertContains(src, "Column(\"created_at\", ColumnKind.TEXT, nullable = true)") // timestamp -> TEXT
    }

    @Test
    fun sqldelight_schema_now_emits_paged_queries() {
        val sq = SupabaseSqlDelightGenerator.generate(fixture, "com.example.db").single().contents
        assertContains(sq, "countAll:\nSELECT COUNT(*) FROM \"todos\";")
        assertContains(sq, "page:\nSELECT * FROM \"todos\" ORDER BY \"id\" LIMIT ? OFFSET ?;")
        assertContains(sq, "pageAfter:\nSELECT * FROM \"todos\" WHERE \"id\" > ? ORDER BY \"id\" LIMIT ?;")
        assertTrue("countAll" in sq)
    }
}
