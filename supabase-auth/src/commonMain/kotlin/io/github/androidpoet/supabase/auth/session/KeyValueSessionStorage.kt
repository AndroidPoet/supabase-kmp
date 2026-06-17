package io.github.androidpoet.supabase.auth.session
import io.github.androidpoet.supabase.auth.models.Session
import kotlinx.serialization.json.Json

/**
 * Minimal persistent key/value backend used by [KeyValueSessionStorage].
 *
 * Implement this over the platform's secure/persistent store so sessions survive
 * process death — for example Keychain (Apple), `EncryptedSharedPreferences` /
 * DataStore (Android), `localStorage` (Wasm/JS), or a file (JVM/desktop). Only
 * three string operations are required; serialization is handled for you.
 */
public interface KeyValueStore {
    public suspend fun get(key: String): String?

    public suspend fun set(key: String, value: String)

    public suspend fun remove(key: String)
}

/**
 * A persistent [SessionStorage] that serializes the [Session] to JSON and defers
 * the actual storage to a [KeyValueStore].
 *
 * The default [InMemorySessionStorage] is fine for tests and short-lived
 * processes, but a real app should persist the session so users stay signed in
 * across restarts:
 *
 * ```kotlin
 * val storage = KeyValueSessionStorage(myKeychainStore)
 * val sessionManager = createSessionManager(auth, client, SessionConfig(storage = storage))
 * ```
 */
public class KeyValueSessionStorage(
    private val store: KeyValueStore,
    private val key: String = DEFAULT_KEY,
) : SessionStorage {
    override suspend fun save(session: Session) {
        store.set(key, json.encodeToString(Session.serializer(), session))
    }

    override suspend fun load(): Session? {
        val raw = store.get(key) ?: return null
        // A corrupt or schema-changed payload should read as "no session" rather
        // than crash the caller; the next sign-in overwrites it.
        val session = runCatching { json.decodeFromString(Session.serializer(), raw) }.getOrNull()
        // Valid JSON can still decode into a structurally unusable session —
        // empty tokens or a negative expiry from partial corruption or a tampered
        // store. Treat those as "no session" too, so the caller doesn't restore a
        // token that can only fail with a surprising 401 on the first request.
        return session?.takeIf(::isUsable)
    }

    // A session is only usable if it carries both tokens and a non-negative
    // lifetime; the refresh token in particular must survive so the session can
    // be renewed after the access token expires.
    private fun isUsable(session: Session): Boolean =
        session.accessToken.isNotBlank() &&
            session.refreshToken.isNotBlank() &&
            session.expiresIn >= 0

    override suspend fun clear() {
        store.remove(key)
    }

    public companion object {
        public const val DEFAULT_KEY: String = "io.github.androidpoet.supabase.session"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
