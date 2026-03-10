package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.models.Session

/**
 * Represents the lifecycle states of a user session.
 *
 * Observe via [SessionManager.sessionState] to drive UI changes
 * (e.g. show login screen vs. main content).
 */
public sealed interface SessionState {

    /** No session — user is not authenticated. */
    public data object NotAuthenticated : SessionState

    /** Session is being loaded/restored from storage. */
    public data object Loading : SessionState

    /** Active session with valid tokens. */
    public data class Authenticated(public val session: Session) : SessionState

    /** Session expired — refresh failed or token invalid. */
    public data class Expired(public val lastSession: Session) : SessionState
}
