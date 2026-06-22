package io.github.androidpoet.supabase.codegen

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseSqlDelightGeneratorTest {
    // PostgREST flags the primary key by appending `<pk/>` to the column description.
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
                "score":      { "format": "int64", "type": "integer" },
                "price":      { "format": "numeric", "type": "number" },
                "created_at": { "format": "timestamp with time zone", "type": "string" },
                "tags":       { "type": "array", "format": "text[]", "items": { "type": "string" } }
              }
            }
          }
        }
        """.trimIndent()

    private val files = SupabaseSqlDelightGenerator.generate(fixture, packageName = "com.example.db")
    private val todos = files.single { it.relativePath.endsWith("Todos.sq") }.contents

    @Test
    fun emits_one_sq_file_per_table_under_the_package_path() {
        assertEquals(1, files.size)
        assertEquals("com/example/db/Todos.sq", files.single().relativePath)
    }

    @Test
    fun creates_the_table_with_sqlite_storage_classes() {
        assertContains(todos, "CREATE TABLE IF NOT EXISTS \"todos\"")
        assertContains(todos, "\"id\" TEXT") // uuid → TEXT
        assertContains(todos, "\"rank\" INTEGER") // int32 → INTEGER
        assertContains(todos, "\"score\" INTEGER") // int64 → INTEGER
        assertContains(todos, "\"done\" INTEGER") // boolean → INTEGER (0/1)
        assertContains(todos, "\"price\" REAL") // numeric → REAL
        assertContains(todos, "\"created_at\" TEXT") // timestamp → TEXT
        assertContains(todos, "\"tags\" TEXT") // array → TEXT (JSON)
    }

    @Test
    fun marks_not_null_and_the_primary_key() {
        assertContains(todos, "\"id\" TEXT NOT NULL PRIMARY KEY") // required + <pk/>
        assertContains(todos, "\"title\" TEXT NOT NULL") // required
        assertTrue("\"created_at\" TEXT NOT NULL" !in todos, "created_at is not required → nullable")
    }

    @Test
    fun emits_standard_queries() {
        assertContains(todos, "selectAll:\nSELECT * FROM \"todos\";")
        assertContains(todos, "selectById:\nSELECT * FROM \"todos\" WHERE \"id\" = ?;")
        assertContains(todos, "deleteById:\nDELETE FROM \"todos\" WHERE \"id\" = ?;")
        assertContains(todos, "upsert:\nINSERT OR REPLACE INTO \"todos\"")
    }

    @Test
    fun falls_back_to_an_id_column_when_no_pk_marker_is_present() {
        val schema =
            """
            {
              "definitions": {
                "events": {
                  "required": ["id"],
                  "properties": {
                    "id":   { "format": "uuid", "type": "string" },
                    "name": { "format": "text", "type": "string" }
                  }
                }
              }
            }
            """.trimIndent()
        val sq = SupabaseSqlDelightGenerator.generate(schema, "p").single().contents
        assertContains(sq, "\"id\" TEXT NOT NULL PRIMARY KEY")
    }
}
