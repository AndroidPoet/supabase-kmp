package io.github.androidpoet.supabase.auth.session

/**
 * Configuration for a [SessionManager].
 *
 * @property autoRefresh refresh the access token in the background before it expires.
 * @property refreshBufferSeconds how many seconds before expiry to refresh.
 * @property storage where the session is persisted. **Defaults to
 *   [InMemorySessionStorage], which does not survive a process restart** — the
 *   user is silently signed out when the app is killed. For production apps
 *   provide a durable [SessionStorage] (e.g. one backed by your own
 *   [KeyValueSessionStorage]); the in-memory default is intended for tests and
 *   short-lived processes.
 */
public data class SessionConfig(
    public val autoRefresh: Boolean = true,
    public val refreshBufferSeconds: Long = 60,
    public val storage: SessionStorage = InMemorySessionStorage(),
)
