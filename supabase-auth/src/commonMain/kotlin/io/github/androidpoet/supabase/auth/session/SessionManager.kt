package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the authenticated session lifecycle: persistence, restoration,
 * automatic token refresh, and reactive state observation.
 *
 * Obtain via Koin using [authModule].
 */
public interface SessionManager {

    /** Current session state as a hot flow. Emits [SessionState.NotAuthenticated] when signed out. */
    public val sessionState: StateFlow<SessionState>

    /** Current session if available, `null` otherwise. */
    public val currentSession: Session?

    /** Current access token if the session is active, `null` otherwise. */
    public val accessToken: String?

    /** Save a new session (after sign-in / sign-up). Starts auto-refresh if enabled. */
    public suspend fun saveSession(session: Session)

    /** Clear the current session (sign-out). Stops auto-refresh. */
    public suspend fun clearSession()

    /** Manually refresh the current session using the stored refresh token. */
    public suspend fun refreshSession(): SupabaseResult<Session>

    /** Restore session from persistent storage (call on app start). */
    public suspend fun restoreSession(): SupabaseResult<Session>

    /** Release resources (cancel auto-refresh coroutine). */
    public fun close()
}
