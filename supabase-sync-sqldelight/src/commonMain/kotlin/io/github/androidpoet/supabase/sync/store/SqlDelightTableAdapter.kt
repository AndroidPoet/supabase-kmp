package io.github.androidpoet.supabase.sync.store

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import io.github.androidpoet.supabase.sync.TableAdapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** The SQLite storage class of a column, plus how its value maps to/from JSON. */
public enum class ColumnKind { TEXT, INTEGER, REAL, BOOL, JSON }

/** One column of a synced table: its [name], its [kind], and whether it is [nullable]. */
public data class Column(
    public val name: String,
    public val kind: ColumnKind,
    public val nullable: Boolean = false,
)

/**
 * A **generic** [TableAdapter] over any SQLite table reachable through a SQLDelight [SqlDriver],
 * driven entirely by a column descriptor ([columns] + [pk]). All the JSON ↔ typed-column conversion
 * lives here, once and tested — so codegen only has to emit the trivial descriptor (table name,
 * columns, primary key), not per-table conversion logic that could drift or fail to compile.
 *
 * Reads and writes go straight to the same SQLite table your generated `.sq` `CREATE TABLE` defines,
 * so your typed SQLDelight queries and the sync engine share one set of rows — a true single source
 * of truth. Booleans are stored as `INTEGER` (0/1) and `json`/`jsonb`/array columns as JSON `TEXT`,
 * matching the schema the SQLDelight generator emits.
 */
public class SqlDelightTableAdapter(
    private val driver: SqlDriver,
    override val table: String,
    private val columns: List<Column>,
    private val pk: String,
) : TableAdapter {
    private val json = Json { ignoreUnknownKeys = true }
    private val quotedTable = quote(table)
    private val columnList = columns.joinToString(", ") { quote(it.name) }

    override fun upsert(id: String, fields: JsonObject) {
        val placeholders = columns.joinToString(", ") { "?" }
        driver.execute(null, "INSERT OR REPLACE INTO $quotedTable($columnList) VALUES($placeholders)", columns.size) {
            columns.forEachIndexed { i, column ->
                bind(i, column, if (column.name == pk) JsonPrimitive(id) else fields[column.name])
            }
        }
    }

    override fun get(id: String): JsonObject? =
        driver
            .executeQuery(
                identifier = null,
                sql = "SELECT $columnList FROM $quotedTable WHERE ${quote(pk)} = ?",
                mapper = { cursor -> QueryResult.Value(if (cursor.next().value) readRow(cursor) else null) },
                parameters = 1,
            ) { bindString(0, id) }
            .value

    override fun delete(id: String) {
        driver.execute(null, "DELETE FROM $quotedTable WHERE ${quote(pk)} = ?", 1) { bindString(0, id) }
    }

    override fun page(limit: Long, offset: Long): List<Pair<String, JsonObject>> =
        driver
            .executeQuery(
                identifier = null,
                sql = "SELECT $columnList FROM $quotedTable ORDER BY ${quote(pk)} LIMIT ? OFFSET ?",
                mapper = ::collectRows,
                parameters = 2,
            ) {
                bindLong(0, limit)
                bindLong(1, offset)
            }.value

    override fun pageAfter(afterId: String?, limit: Long): List<Pair<String, JsonObject>> =
        driver
            .executeQuery(
                identifier = null,
                sql = "SELECT $columnList FROM $quotedTable WHERE ${quote(pk)} > ? ORDER BY ${quote(pk)} LIMIT ?",
                mapper = ::collectRows,
                parameters = 2,
            ) {
                bindString(0, afterId ?: "")
                bindLong(1, limit)
            }.value

    override fun count(): Long =
        driver
            .executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM $quotedTable",
                mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
                parameters = 0,
            ).value

    private fun collectRows(cursor: SqlCursor): QueryResult<List<Pair<String, JsonObject>>> {
        val rows = mutableListOf<Pair<String, JsonObject>>()
        while (cursor.next().value) {
            val row = readRow(cursor)
            rows += (row[pk] as? JsonPrimitive)?.content.orEmpty() to row
        }
        return QueryResult.Value(rows)
    }

    private fun readRow(cursor: SqlCursor): JsonObject =
        buildJsonObject {
            columns.forEachIndexed { i, column -> put(column.name, cursorValue(cursor, i, column)) }
        }

    private fun cursorValue(cursor: SqlCursor, index: Int, column: Column): JsonElement =
        when (column.kind) {
            ColumnKind.TEXT -> cursor.getString(index)?.let { JsonPrimitive(it) } ?: JsonNull
            ColumnKind.INTEGER -> cursor.getLong(index)?.let { JsonPrimitive(it) } ?: JsonNull
            ColumnKind.REAL -> cursor.getDouble(index)?.let { JsonPrimitive(it) } ?: JsonNull
            ColumnKind.BOOL -> cursor.getLong(index)?.let { JsonPrimitive(it != 0L) } ?: JsonNull
            ColumnKind.JSON -> cursor.getString(index)?.let { json.parseToJsonElement(it) } ?: JsonNull
        }

    private fun app.cash.sqldelight.db.SqlPreparedStatement.bind(index: Int, column: Column, value: JsonElement?) {
        if (value == null || value is JsonNull) {
            // Bind a typed null so the column's affinity is preserved.
            when (column.kind) {
                ColumnKind.TEXT, ColumnKind.JSON -> bindString(index, null)
                ColumnKind.INTEGER, ColumnKind.BOOL -> bindLong(index, null)
                ColumnKind.REAL -> bindDouble(index, null)
            }
            return
        }
        when (column.kind) {
            ColumnKind.TEXT -> bindString(index, value.jsonPrimitive.content)
            ColumnKind.INTEGER -> bindLong(index, value.jsonPrimitive.long)
            ColumnKind.REAL -> bindDouble(index, value.jsonPrimitive.double)
            ColumnKind.BOOL -> bindLong(index, if (value.jsonPrimitive.boolean) 1L else 0L)
            ColumnKind.JSON -> bindString(index, value.toString())
        }
    }

    private fun quote(identifier: String): String = "\"$identifier\""
}
