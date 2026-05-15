package io.github.androidpoet.supabase.auth.session
public data class SessionConfig(
    public val autoRefresh: Boolean = true,
    public val refreshBufferSeconds: Long = 60,
    public val storage: SessionStorage = InMemorySessionStorage(),
)
