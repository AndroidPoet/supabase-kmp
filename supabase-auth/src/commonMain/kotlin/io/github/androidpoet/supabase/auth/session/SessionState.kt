package io.github.androidpoet.supabase.auth.session
import io.github.androidpoet.supabase.auth.models.Session
public sealed interface SessionState {
    public data object NotAuthenticated : SessionState
    public data object Loading : SessionState
    public data class Authenticated(public val session: Session) : SessionState
    public data class Expired(public val lastSession: Session) : SessionState
}
