package io.github.androidpoet.supabase.codegen

import kotlinx.serialization.Serializable

/**
 * The slice of the PostgREST OpenAPI (Swagger 2.0) document we need. Every Supabase
 * project serves this at `GET {projectUrl}/rest/v1/`: each table is a `definitions`
 * entry whose `properties` describe its columns and whose `required` array lists the
 * NOT NULL columns. Unknown fields are ignored on parse.
 */
@Serializable
internal data class OpenApiSpec(
    val definitions: Map<String, TableDefinition> = emptyMap(),
)

/** One table: its columns ([properties]) and the names of its NOT NULL columns ([required]). */
@Serializable
internal data class TableDefinition(
    val required: List<String> = emptyList(),
    val properties: Map<String, ColumnProperty> = emptyMap(),
)

/**
 * One column. [format] is the raw Postgres type name (e.g. `uuid`, `int8`,
 * `timestamp with time zone`) and is what we map to a Kotlin type; [type] is the
 * coarse JSON type. A non-empty [enum] means the column is a Postgres enum. [items]
 * describes the element type when [type] is `array`.
 */
@Serializable
internal data class ColumnProperty(
    val type: String? = null,
    val format: String? = null,
    val description: String? = null,
    val enum: List<String> = emptyList(),
    val items: ArrayItems? = null,
)

/** The element type of an array column. */
@Serializable
internal data class ArrayItems(
    val type: String? = null,
    val format: String? = null,
    val enum: List<String> = emptyList(),
)
