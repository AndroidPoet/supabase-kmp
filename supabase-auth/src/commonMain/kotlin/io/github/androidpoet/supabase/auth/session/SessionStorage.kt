package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.models.Session

/**
 * Platform-agnostic session persistence interface.
 *
 * Implement per-platform for secure storage (Android Keystore, iOS Keychain, etc.).
 * The default [InMemorySessionStorage] keeps sessions only in memory.
 */
public interface SessionStorage {
    public suspend fun save(session: Session)
    public suspend fun load(): Session?
    public suspend fun clear()
}
