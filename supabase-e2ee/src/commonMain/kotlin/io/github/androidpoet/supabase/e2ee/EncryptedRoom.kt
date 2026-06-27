@file:OptIn(ExperimentalEncodingApi::class)

package io.github.androidpoet.supabase.e2ee

import io.github.androidpoet.supabase.core.models.Column
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.flatMap
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Synthetic error codes raised by [EncryptedRoom]. */
public object E2eeErrorCodes {
    /** Strict mode refused to send to an unverified peer. */
    public const val UNVERIFIED: String = "e2ee_unverified"

    /** The peer has not published a public key. */
    public const val MISSING_KEYS: String = "e2ee_missing_keys"

    /** The peer's published key differs from the one previously trusted. */
    public const val IDENTITY_CHANGED: String = "e2ee_identity_changed"
}

/** A decrypted chat message read back from the encrypted [EncryptedRoom]. */
public class DecryptedMessage(
    /** Server row id. */
    public val id: String,
    /** The author's user id. */
    public val senderId: String,
    /** The recovered clear text, or `null` if absent / undecryptable. */
    public val plaintext: String?,
    /** Whether decryption was attempted and failed. */
    public val decryptFailed: Boolean,
    /** Creation timestamp string from the row, if present. */
    public val createdAt: String?,
)

/**
 * A 1:1 end-to-end encrypted room over a Supabase `messages` table.
 *
 * Bodies are encrypted with a shared AES-256-GCM key (ECDH → HKDF) before they
 * leave the device; the server only ever stores ciphertext. Because the shared
 * key is symmetric, both peers decrypt the same rows — including their own.
 *
 * **Verify first.** In the default strict mode, [send] refuses until the peer's
 * [safetyNumber] has been confirmed out of band and [markVerified] called —
 * this is what prevents a tampered key directory from mounting a
 * man-in-the-middle. Open one with [openEncryptedRoom].
 */
public class EncryptedRoom internal constructor(
    private val database: DatabaseClient,
    private val realtime: RealtimeClient,
    private val session: E2eeSession,
    private val myPublicKey: ByteArray,
    /** The local user id. */
    public val myUserId: String,
    /** The peer's user id. */
    public val peerUserId: String,
    private val peerPublicKey: ByteArray,
    private val trustStore: TrustStore,
    /** The room this instance is scoped to. */
    public val roomId: String,
    private val requireVerified: Boolean,
    private val messagesTable: String,
) {
    /** The safety number to compare out of band with the peer. */
    public suspend fun safetyNumber(): String =
        safetyNumber(myPublicKey, peerPublicKey)

    /** Whether the peer's key has been verified. */
    public suspend fun isVerified(): Boolean =
        trustStore.get(peerUserId)?.level == TrustLevel.VERIFIED

    /** Marks the peer verified — call only after confirming [safetyNumber]. */
    public suspend fun markVerified() {
        val entry =
            trustStore.get(peerUserId)
                ?: TrustEntry(peerPublicKey, TrustLevel.VERIFIED)
        trustStore.put(peerUserId, entry.withLevel(TrustLevel.VERIFIED))
    }

    /**
     * Encrypts [text] and inserts it as ciphertext. In strict mode, fails with
     * [E2eeErrorCodes.UNVERIFIED] if the peer is not yet verified.
     */
    public suspend fun send(text: String): SupabaseResult<Unit> {
        if (requireVerified && !isVerified()) {
            return SupabaseResult.Failure(
                SupabaseError(
                    message = "refusing to encrypt to unverified peer $peerUserId",
                    code = E2eeErrorCodes.UNVERIFIED,
                ),
            )
        }
        return session.encrypt(text).flatMap { ciphertext ->
            val body =
                buildJsonObject {
                    put("room_id", roomId)
                    put("sender_id", myUserId)
                    put("ciphertext", Base64.encode(ciphertext))
                }.toString()
            database.insert(table = messagesTable, body = body).map { }
        }
    }

    /** Loads and decrypts the existing messages in this room. */
    public suspend fun history(): SupabaseResult<List<DecryptedMessage>> =
        database
            .select(table = messagesTable, columns = "*") {
                where { Column<String>("room_id") eq roomId }
            }.map { rowsJson ->
                Json
                    .parseToJsonElement(rowsJson)
                    .jsonArray
                    .map { decodeRow(it.jsonObject) }
            }

    /**
     * A live stream of newly-inserted messages, decrypted on the fly. Collect
     * after [history] for the full timeline.
     */
    public fun messages(): Flow<DecryptedMessage> =
        callbackFlow {
            realtime.connect()
            val subscription =
                realtime
                    .channel("e2ee:$roomId")
                    .onPostgresChange(
                        schema = "public",
                        table = messagesTable,
                        filter = "room_id=eq.$roomId",
                        event = PostgresChangeEvent.INSERT,
                    ) { payload ->
                        val record = payload["record"]?.jsonObject
                        if (record != null) trySend(decodeRow(record))
                    }.subscribe()
            awaitClose {
                launch { runCatching { subscription.unsubscribe() } }
            }
        }

    private suspend fun decodeRow(row: JsonObject): DecryptedMessage {
        val id = row["id"]?.jsonPrimitive?.content.orEmpty()
        val senderId = row["sender_id"]?.jsonPrimitive?.content.orEmpty()
        val createdAt = row["created_at"]?.jsonPrimitive?.content
        val ciphertext =
            row["ciphertext"]?.jsonPrimitive?.content
                ?: return DecryptedMessage(id, senderId, null, false, createdAt)
        return when (val decrypted = session.decryptToString(Base64.decode(ciphertext))) {
            is SupabaseResult.Success ->
                DecryptedMessage(id, senderId, decrypted.value, false, createdAt)
            is SupabaseResult.Failure ->
                DecryptedMessage(id, senderId, null, true, createdAt)
        }
    }
}

/**
 * Opens an [EncryptedRoom] for a 1:1 conversation: publishes the local public
 * key, fetches the peer's, records it (trust-on-first-use), rejects a changed
 * key as [E2eeErrorCodes.IDENTITY_CHANGED], and derives the shared session.
 *
 * The returned room is **unverified** until you compare [EncryptedRoom.safetyNumber]
 * out of band and call [EncryptedRoom.markVerified]; in the default strict mode
 * sending is blocked until then.
 */
public suspend fun openEncryptedRoom(
    database: DatabaseClient,
    realtime: RealtimeClient,
    keyDirectory: KeyDirectory,
    myKeyPair: E2eeKeyPair,
    myUserId: String,
    peerUserId: String,
    roomId: String,
    trustStore: TrustStore = InMemoryTrustStore(),
    requireVerified: Boolean = true,
    messagesTable: String = "e2ee_messages",
): SupabaseResult<EncryptedRoom> {
    // Publish own key, then fetch the peer's — chained so a failure short-circuits.
    val fetched =
        keyDirectory
            .publish(myUserId, myKeyPair.publicKey)
            .flatMap { keyDirectory.fetch(peerUserId) }
    val peerKey =
        when (fetched) {
            is SupabaseResult.Failure -> return fetched
            is SupabaseResult.Success ->
                fetched.value
                    ?: return SupabaseResult.Failure(
                        SupabaseError(
                            message = "no published key for $peerUserId",
                            code = E2eeErrorCodes.MISSING_KEYS,
                        ),
                    )
        }

    // Trust on first use; reject a later key change as tampering.
    val existing = trustStore.get(peerUserId)
    if (existing == null) {
        trustStore.put(peerUserId, TrustEntry(peerKey, TrustLevel.UNVERIFIED))
    } else if (!existing.publicKey.contentEquals(peerKey)) {
        return SupabaseResult.Failure(
            SupabaseError(
                message = "identity key for $peerUserId changed; re-verify",
                code = E2eeErrorCodes.IDENTITY_CHANGED,
            ),
        )
    }

    // Derive the session; a derive failure flows through `map` as Failure.
    return myKeyPair.deriveSession(peerKey).map { session ->
        EncryptedRoom(
            database = database,
            realtime = realtime,
            session = session,
            myPublicKey = myKeyPair.publicKey,
            myUserId = myUserId,
            peerUserId = peerUserId,
            peerPublicKey = peerKey,
            trustStore = trustStore,
            roomId = roomId,
            requireVerified = requireVerified,
            messagesTable = messagesTable,
        )
    }
}
