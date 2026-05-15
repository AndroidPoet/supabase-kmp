package io.github.androidpoet.supabase.auth.session
import io.github.androidpoet.supabase.auth.models.Session
public interface SessionStorage {
    public suspend fun save(session: Session)
    public suspend fun load(): Session?
    public suspend fun clear()
}
