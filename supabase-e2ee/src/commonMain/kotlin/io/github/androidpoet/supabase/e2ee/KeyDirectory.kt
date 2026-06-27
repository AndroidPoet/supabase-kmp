@file:OptIn(ExperimentalEncodingApi::class)

package io.github.androidpoet.supabase.e2ee

import io.github.androidpoet.supabase.core.models.Column
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.database.DatabaseClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Publishes and serves users' **public** E2EE keys, so a sender can fetch a
 * recipient's key and derive a shared session. Only public key material is ever
 * handled — private keys never leave the device.
 *
 * The public key alone is not proof of identity: authenticate it with the
 * [safetyNumber] before trusting it (see [EncryptedRoom]).
 */
public interface KeyDirectory {
    /** Publishes (replaces) [userId]'s public key. */
    public suspend fun publish(userId: String, publicKey: ByteArray): SupabaseResult<Unit>

    /** Fetches [userId]'s published public key, or `null` if none. */
    public suspend fun fetch(userId: String): SupabaseResult<ByteArray?>
}

/**
 * A [KeyDirectory] backed by a Supabase `device_keys` table (one row per user,
 * the public key stored base64-encoded). RLS should allow anyone to read keys
 * (they are public) but only let a user write their own row — see the package
 * migration.
 */
public class SupabaseKeyDirectory(
    private val database: DatabaseClient,
    private val table: String = "device_keys",
) : KeyDirectory {
    override suspend fun publish(
        userId: String,
        publicKey: ByteArray,
    ): SupabaseResult<Unit> {
        val body =
            buildJsonObject {
                put("user_id", userId)
                put("public_key", Base64.encode(publicKey))
            }.toString()
        return database
            .insert(
                table = table,
                body = body,
                upsert = true,
                onConflict = "user_id",
            ).map { }
    }

    override suspend fun fetch(userId: String): SupabaseResult<ByteArray?> =
        database
            .select(table = table, columns = "public_key") {
                where { Column<String>("user_id") eq userId }
            }.map { rowsJson ->
                val rows = Json.parseToJsonElement(rowsJson).jsonArray
                val key =
                    rows
                        .firstOrNull()
                        ?.jsonObject
                        ?.get("public_key")
                        ?.jsonPrimitive
                        ?.content
                key?.let { Base64.decode(it) }
            }
}

/** A non-persistent [KeyDirectory] for tests and local prototyping. */
public class InMemoryKeyDirectory : KeyDirectory {
    private val mutex = Mutex()
    private val keys = mutableMapOf<String, ByteArray>()

    override suspend fun publish(
        userId: String,
        publicKey: ByteArray,
    ): SupabaseResult<Unit> =
        mutex.withLock {
            keys[userId] = publicKey
            SupabaseResult.Success(Unit)
        }

    override suspend fun fetch(userId: String): SupabaseResult<ByteArray?> =
        mutex.withLock { SupabaseResult.Success(keys[userId]) }
}
