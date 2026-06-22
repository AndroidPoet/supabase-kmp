package io.github.androidpoet.supabase.codegen

import kotlinx.serialization.json.Json

/**
 * Generates the glue that wires each SQLDelight table (see [SupabaseSqlDelightGenerator]) into an
 * [offline-sync](https://github.com/AndroidPoet/offline-sync-kmp) `LocalStore` as its **single
 * source of truth**: one `SupabaseAdapters.kt` holding a `supabaseAdapters(driver)` factory that
 * returns a `Map<String, TableAdapter>`.
 *
 * Each entry is a generic `SqlDelightTableAdapter` configured with a trivial column descriptor
 * (name + storage kind + nullability) and the primary key — *no* per-table conversion logic is
 * generated, because that lives once in offline-sync's adapter. The generated file therefore only
 * needs the offline-sync runtime + SQLDelight runtime on the classpath to compile.
 */
public object SupabaseSqlDelightAdapterGenerator {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /** The offline-sync runtime package the generated factory imports from. */
    private const val RUNTIME = "io.github.androidpoet.offlinesync"

    /** Parses the OpenAPI [specJson] and returns the single `SupabaseAdapters.kt` [GeneratedFile]. */
    public fun generate(specJson: String, packageName: String): GeneratedFile =
        generate(json.decodeFromString(OpenApiSpec.serializer(), specJson), packageName)

    internal fun generate(spec: OpenApiSpec, packageName: String): GeneratedFile {
        val tables = spec.definitions.filterValues { it.properties.isNotEmpty() }
        val sb = StringBuilder()
        sb.append("package $packageName\n\n")
        sb.append("import app.cash.sqldelight.db.SqlDriver\n")
        sb.append("import $RUNTIME.TableAdapter\n")
        sb.append("import $RUNTIME.store.Column\n")
        sb.append("import $RUNTIME.store.ColumnKind\n")
        sb.append("import $RUNTIME.store.SqlDelightTableAdapter\n\n")
        sb.append("// Generated from the Supabase schema. Do not edit by hand.\n")
        sb.append("// Each table is wired into an offline-sync LocalStore as its source of truth.\n")
        sb.append("public fun supabaseAdapters(driver: SqlDriver): Map<String, TableAdapter> =\n")
        sb.append("    mapOf(\n")
        for ((tableName, definition) in tables) {
            val pk = primaryKeyColumn(definition) ?: definition.properties.keys.first()
            sb.append("        ${quoteString(tableName)} to SqlDelightTableAdapter(\n")
            sb.append("            driver = driver,\n")
            sb.append("            table = ${quoteString(tableName)},\n")
            sb.append("            columns =\n")
            sb.append("                listOf(\n")
            for ((name, column) in definition.properties) {
                val kind = columnKind(column)
                val nullable = name !in definition.required
                sb.append("                    Column(${quoteString(name)}, ColumnKind.$kind, nullable = $nullable),\n")
            }
            sb.append("                ),\n")
            sb.append("            pk = ${quoteString(pk)},\n")
            sb.append("        ),\n")
        }
        sb.append("    )\n")
        return GeneratedFile("${packageName.replace('.', '/')}/SupabaseAdapters.kt", sb.toString())
    }

    /** Mirrors [SupabaseSqlDelightGenerator]: PostgREST marks the PK with `<pk/>`, else fall to `id`. */
    private fun primaryKeyColumn(definition: TableDefinition): String? {
        definition.properties.entries
            .firstOrNull { (_, c) -> c.description?.contains("<pk/>") == true }
            ?.let { return it.key }
        return definition.properties.keys.firstOrNull { it == "id" }
    }

    /**
     * Maps a Postgres column to a [ColumnKind] — the SQLite storage class *plus* how the value maps
     * to/from JSON. Unlike the raw `.sq` storage class, `boolean` and `json`/`jsonb`/array stay
     * distinct from plain `INTEGER`/`TEXT` so the adapter can round-trip them faithfully.
     */
    private fun columnKind(column: ColumnProperty): String {
        if (column.type == "array") return "JSON" // arrays stored as JSON text
        return when (column.format?.lowercase()?.trim() ?: column.type?.lowercase()?.trim()) {
            in BOOL_TYPES -> "BOOL"
            in INT_TYPES, in LONG_TYPES -> "INTEGER"
            in REAL_TYPES -> "REAL"
            in JSON_TYPES -> "JSON"
            // text, varchar, uuid, timestamp*, date, money, enum, … → TEXT
            else -> "TEXT"
        }
    }

    private fun quoteString(value: String): String = "\"$value\""

    private val INT_TYPES = setOf("int32", "integer", "int4", "smallint", "int2", "serial", "serial4", "smallserial")
    private val LONG_TYPES = setOf("int64", "bigint", "int8", "bigserial", "serial8")
    private val REAL_TYPES = setOf("number", "double precision", "float8", "real", "float4", "numeric", "decimal")
    private val BOOL_TYPES = setOf("boolean", "bool")
    private val JSON_TYPES = setOf("json", "jsonb")
}
