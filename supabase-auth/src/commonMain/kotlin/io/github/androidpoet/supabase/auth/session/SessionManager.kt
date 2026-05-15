package io.github.androidpoet.supabase.auth.session
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.StateFlow
public interface SessionManager {
    public val sessionState: StateFlow<SessionState>
    public val currentSession: Session?
    public val accessToken: String?
    public suspend fun saveSession(session: Session)
    public suspend fun clearSession()
    public suspend fun refreshSession(): SupabaseResult<Session>
    public suspend fun restoreSession(): SupabaseResult<Session>
    public fun close()
}
