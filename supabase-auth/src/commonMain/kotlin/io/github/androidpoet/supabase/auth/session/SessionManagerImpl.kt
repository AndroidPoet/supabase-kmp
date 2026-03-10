package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Internal implementation of [SessionManager].
 *
 * Handles token persistence via [SessionStorage], automatic background
 * refresh, and propagation of the access token to [SupabaseClient].
 */
internal class SessionManagerImpl(
    private val authClient: AuthClient,
    private val supabaseClient: SupabaseClient,
    private val storage: SessionStorage = InMemorySessionStorage(),
    private val autoRefresh: Boolean = true,
    private val refreshBufferSeconds: Long = 60,
) : SessionManager {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.NotAuthenticated)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override val currentSession: Session?
        get() = (_sessionState.value as? SessionState.Authenticated)?.session

    override val accessToken: String?
        get() = currentSession?.accessToken

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    // ── Public API ──────────────────────────────────────────────────

    override suspend fun saveSession(session: Session) {
        storage.save(session)
        _sessionState.value = SessionState.Authenticated(session)
        supabaseClient.setAccessToken(session.accessToken)
        scheduleRefresh(session)
    }

    override suspend fun clearSession() {
        cancelRefresh()
        storage.clear()
        _sessionState.value = SessionState.NotAuthenticated
        supabaseClient.clearAccessToken()
    }

    override suspend fun refreshSession(): SupabaseResult<Session> {
        val current = currentSession
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session to refresh"))

        return handleRefresh(current)
    }

    override suspend fun restoreSession(): SupabaseResult<Session> {
        _sessionState.value = SessionState.Loading

        val stored = storage.load()
        if (stored == null) {
            _sessionState.value = SessionState.NotAuthenticated
            return SupabaseResult.Failure(SupabaseError(message = "No stored session found"))
        }

        // Always attempt a refresh to guarantee a fresh access token.
        return handleRefresh(stored)
    }

    override fun close() {
        cancelRefresh()
        scope.cancel()
    }

    // ── Internals ───────────────────────────────────────────────────

    private suspend fun handleRefresh(session: Session): SupabaseResult<Session> =
        when (val result = authClient.refreshToken(session.refreshToken)) {
            is SupabaseResult.Success -> {
                saveSession(result.value)
                result
            }
            is SupabaseResult.Failure -> {
                _sessionState.value = SessionState.Expired(session)
                result
            }
        }

    private fun scheduleRefresh(session: Session) {
        if (!autoRefresh) return
        cancelRefresh()

        val delayMs = maxOf((session.expiresIn - refreshBufferSeconds) * 1000L, 0L)
        refreshJob = scope.launch {
            delay(delayMs)
            refreshSession()
        }
    }

    private fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }
}
