package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.models.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

public enum class AuthStateChangeEvent {
    INITIAL_SESSION,
    SIGNED_IN,
    SIGNED_OUT,
    TOKEN_REFRESHED,
    USER_UPDATED,
    PASSWORD_RECOVERY,
    MFA_CHALLENGE_VERIFIED,
}

public fun SessionManager.onAuthStateChange(
    scope: CoroutineScope,
    emitInitialSession: Boolean = true,
    callback: suspend (event: AuthStateChangeEvent, session: Session?) -> Unit,
): Job = scope.launch {
    var previousState = sessionState.value
    if (emitInitialSession) {
        callback(AuthStateChangeEvent.INITIAL_SESSION, previousState.sessionOrNull())
    }
    sessionState.drop(1).collect { state ->
        val event = state.toAuthStateChangeEvent(previousState) ?: return@collect
        previousState = state
        callback(event, state.sessionOrNull())
    }
}

private fun SessionState.sessionOrNull(): Session? =
    when (this) {
        is SessionState.Authenticated -> session
        is SessionState.Expired -> lastSession
        SessionState.Loading,
        SessionState.NotAuthenticated,
        -> null
    }

private fun SessionState.toAuthStateChangeEvent(previous: SessionState): AuthStateChangeEvent? =
    when (this) {
        is SessionState.Authenticated -> {
            val previousSession = (previous as? SessionState.Authenticated)?.session
            when {
                previousSession == null -> AuthStateChangeEvent.SIGNED_IN
                previousSession.user != session.user -> AuthStateChangeEvent.USER_UPDATED
                previousSession.accessToken != session.accessToken ||
                    previousSession.refreshToken != session.refreshToken -> AuthStateChangeEvent.TOKEN_REFRESHED
                else -> null
            }
        }
        is SessionState.Expired -> AuthStateChangeEvent.SIGNED_OUT
        SessionState.NotAuthenticated -> {
            if (previous is SessionState.Authenticated || previous is SessionState.Expired) {
                AuthStateChangeEvent.SIGNED_OUT
            } else {
                null
            }
        }
        SessionState.Loading -> null
    }
