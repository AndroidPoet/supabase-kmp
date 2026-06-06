package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerExtTest {
    @Test
    fun test_onAuthStateChange_emitsJsStyleEventsFromSessionState() = runTest {
        val manager = StateFakeSessionManager()
        val events = mutableListOf<AuthStateChangeEvent>()
        val sessions = mutableListOf<Session?>()
        val job = manager.onAuthStateChange(this) { event, session ->
            events += event
            sessions += session
        }
        runCurrent()

        manager.emit(SessionState.Authenticated(testSession(accessToken = "acc-1", refreshToken = "ref-1")))
        runCurrent()
        manager.emit(SessionState.Authenticated(testSession(accessToken = "acc-2", refreshToken = "ref-2")))
        runCurrent()
        manager.emit(SessionState.NotAuthenticated)
        runCurrent()
        job.cancelAndJoin()

        assertEquals(
            listOf(
                AuthStateChangeEvent.INITIAL_SESSION,
                AuthStateChangeEvent.SIGNED_IN,
                AuthStateChangeEvent.TOKEN_REFRESHED,
                AuthStateChangeEvent.SIGNED_OUT,
            ),
            events,
        )
        assertEquals(null, sessions[0])
        assertEquals("acc-1", sessions[1]?.accessToken)
        assertEquals("acc-2", sessions[2]?.accessToken)
        assertEquals(null, sessions[3])
    }
}

private fun testSession(
    accessToken: String,
    refreshToken: String,
): Session = Session(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresIn = 3600,
    tokenType = "bearer",
    user = User(id = "user-1"),
)

private class StateFakeSessionManager : SessionManager {
    private val state = MutableStateFlow<SessionState>(SessionState.NotAuthenticated)
    override val sessionState: StateFlow<SessionState> = state
    override val currentSession: Session?
        get() = (state.value as? SessionState.Authenticated)?.session
    override val accessToken: String?
        get() = currentSession?.accessToken

    fun emit(sessionState: SessionState) {
        state.value = sessionState
    }

    override suspend fun saveSession(session: Session) {
        state.value = SessionState.Authenticated(session)
    }

    override suspend fun clearSession() {
        state.value = SessionState.NotAuthenticated
    }

    override suspend fun refreshSession(): SupabaseResult<Session> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun restoreSession(): SupabaseResult<Session> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun initialize(): SupabaseResult<Session> =
        restoreSession()

    override fun startAutoRefresh() = Unit
    override fun stopAutoRefresh() = Unit
    override fun dispose() = Unit
    override fun close() = Unit
}
