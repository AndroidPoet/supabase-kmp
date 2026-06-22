package io.github.androidpoet.supabase.codegen

import kotlinx.serialization.json.Json

/**
 * Generates SQLDelight `.sq` files (one per table) from a PostgREST OpenAPI document, so a local
 * SQLDelight database can mirror the Supabase schema. Each file holds a `CREATE TABLE` plus a small
 * set of standard queries; SQLDelight's own Gradle plugin then turns these into the type-safe
 * Kotlin database. This is the schema half of the "Supabase → SQLDelight" pipeline — it only ever
 * *reads* the schema and emits `.sq` source.
 *
 * Like the Kotlin model generator, the output is fully owned by the generator and overwritten on
 * every run — don't hand-edit it.
 */
public object SupabaseSqlDelightGenerator {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val header =
        """
        |-- Generated from the Supabase schema. Do not edit by hand.
        |-- SQLDelight's plugin turns this into a type-safe Kotlin database.
        """.trimMargin()

    /**
     * Parses the OpenAPI [specJson] and returns one [GeneratedFile] per table, each a `.sq` file
     * placed under [packageName] (SQLDelight derives the package from the directory path).
     */
    public fun generate(specJson: String, packageName: String): List<GeneratedFile> =
        generate(json.decodeFromString(OpenApiSpec.serializer(), specJson), packageName)

    internal fun generate(spec: OpenApiSpec, packageName: String): List<GeneratedFile> {
        val packagePath = packageName.replace('.', '/')
        val files = mutableListOf<GeneratedFile>()
        // Two tables can normalise to the same `.sq` file name (e.g. `user_profile` / `userProfile`),
        // which would emit two files with the same SQLDelight queries class — fail fast instead.
        val claimedFiles = mutableMapOf<String, String>()

        for ((tableName, definition) in spec.definitions) {
            if (definition.properties.isEmpty()) continue
            val fileBase = Naming.pascal(tableName)
            val clash = claimedFiles.put(fileBase, tableName)
            check(clash == null) {
                "Tables `$clash` and `$tableName` both map to the SQLDelight file `$fileBase.sq`. " +
                    "Rename one of the tables (or its exposed name) so the generated names don't collide."
            }
            files += GeneratedFile("$packagePath/$fileBase.sq", buildSqFile(tableName, definition))
        }
        return files
    }

    private fun buildSqFile(tableName: String, definition: TableDefinition): String {
        val pk = primaryKeyColumn(definition)
        val columns =
            definition.properties.entries.joinToString(",\n") { (name, column) ->
                val type = sqliteType(column)
                val notNull = if (name in definition.required) " NOT NULL" else ""
                val pkClause = if (name == pk) " PRIMARY KEY" else ""
                "  ${quote(name)} $type$notNull$pkClause"
            }

        val sb = StringBuilder()
        sb.append(header).append("\n\n")
        sb.append("CREATE TABLE IF NOT EXISTS ${quote(tableName)} (\n").append(columns).append("\n);\n")

        sb.append("\nselectAll:\nSELECT * FROM ${quote(tableName)};\n")
        sb.append("\ncountAll:\nSELECT COUNT(*) FROM ${quote(tableName)};\n")
        if (pk != null) {
            sb.append("\nselectById:\nSELECT * FROM ${quote(tableName)} WHERE ${quote(pk)} = ?;\n")
            sb.append("\ndeleteById:\nDELETE FROM ${quote(tableName)} WHERE ${quote(pk)} = ?;\n")
            // Offset + keyset pagination, ordered by the primary key.
            sb.append("\npage:\nSELECT * FROM ${quote(tableName)} ORDER BY ${quote(pk)} LIMIT ? OFFSET ?;\n")
            sb.append("\npageAfter:\nSELECT * FROM ${quote(tableName)} WHERE ${quote(pk)} > ? ORDER BY ${quote(pk)} LIMIT ?;\n")
        }
        val cols = definition.properties.keys
        val colList = cols.joinToString(", ") { quote(it) }
        val placeholders = cols.joinToString(", ") { "?" }
        sb.append("\nupsert:\nINSERT OR REPLACE INTO ${quote(tableName)}($colList)\nVALUES($placeholders);\n")
        return sb.toString()
    }

    /**
     * PostgREST flags a primary-key column by appending `<pk/>` to its `description`
     * (e.g. `"Note:\nThis is a Primary Key.<pk/>"`). Fall back to a column literally named `id`.
     */
    private fun primaryKeyColumn(definition: TableDefinition): String? {
        definition.properties.entries
            .firstOrNull { (_, c) -> c.description?.contains("<pk/>") == true }
            ?.let { return it.key }
        return definition.properties.keys.firstOrNull { it == "id" }
    }

    /** Wraps an identifier in double quotes so SQL keywords / mixed-case names stay valid. */
    private fun quote(identifier: String): String = "\"$identifier\""

    /** Maps a Postgres column to a SQLite storage class. */
    private fun sqliteType(column: ColumnProperty): String {
        if (column.type == "array") return "TEXT" // arrays are stored as JSON text
        return when (column.format?.lowercase()?.trim() ?: column.type?.lowercase()?.trim()) {
            in INT_TYPES, in LONG_TYPES, in BOOL_TYPES -> "INTEGER"
            in REAL_TYPES -> "REAL"
            // text, varchar, uuid, timestamp*, date, json/jsonb, money, enum, … → TEXT
            else -> "TEXT"
        }
    }

    // Mirrors the Kotlin generator's mapping, collapsed to SQLite's storage classes. `money` is
    // TEXT (PostgREST emits it as a locale-formatted string); `boolean` is INTEGER (0/1).
    private val INT_TYPES = setOf("int32", "integer", "int4", "smallint", "int2", "serial", "serial4", "smallserial")
    private val LONG_TYPES = setOf("int64", "bigint", "int8", "bigserial", "serial8")
    private val REAL_TYPES = setOf("number", "double precision", "float8", "real", "float4", "numeric", "decimal")
    private val BOOL_TYPES = setOf("boolean", "bool")
}
