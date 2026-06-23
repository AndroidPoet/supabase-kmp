package io.github.androidpoet.supabase.sync.store

import io.github.androidpoet.supabase.sync.TableAdapter
import kotlinx.serialization.json.JsonObject

/**
 * An in-memory [TableAdapter] standing in for a consumer's typed SQLDelight/Room table. The real
 * (codegen-generated) adapter would back these calls with typed queries; this proves the store
 * routes the SSOT path through the adapter rather than the blob cache.
 */
internal class FakeTableAdapter(
    override val table: String,
) : TableAdapter {
    val rows = linkedMapOf<String, JsonObject>()

    override fun upsert(id: String, fields: JsonObject) {
        rows[id] = fields
    }

    override fun get(id: String): JsonObject? = rows[id]

    override fun delete(id: String) {
        rows.remove(id)
    }

    override fun page(limit: Long, offset: Long): List<Pair<String, JsonObject>> =
        rows.entries
            .sortedBy { it.key }
            .drop(offset.toInt())
            .take(limit.toInt())
            .map { it.key to it.value }

    override fun pageAfter(afterId: String?, limit: Long): List<Pair<String, JsonObject>> =
        rows.entries
            .sortedBy { it.key }
            .filter { afterId == null || it.key > afterId }
            .take(limit.toInt())
            .map { it.key to it.value }

    override fun count(): Long = rows.size.toLong()
}
