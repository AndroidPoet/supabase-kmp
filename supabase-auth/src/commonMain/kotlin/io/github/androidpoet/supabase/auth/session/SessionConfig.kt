package io.github.androidpoet.supabase.auth.session

/**
 * Configuration for session management.
 *
 * Pass to [authModule] to customise refresh behaviour and storage backend.
 */
public data class SessionConfig(
    /** Whether to automatically refresh tokens before expiry. Default: true. */
    public val autoRefresh: Boolean = true,
    /** Seconds before token expiry to trigger refresh. Default: 60. */
    public val refreshBufferSeconds: Long = 60,
    /** Custom session storage implementation. Default: [InMemorySessionStorage]. */
    public val storage: SessionStorage = InMemorySessionStorage(),
)
