package io.github.androidpoet.supabase.auth.session
import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.accessTokenExpiryEpochSeconds
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock

internal class SessionManagerImpl(
    private val authClient: AuthClient,
    private val supabaseClient: SupabaseClient,
    private val storage: SessionStorage = InMemorySessionStorage(),
    autoRefresh: Boolean = true,
    private val refreshBufferSeconds: Long = 60,
) : SessionManager {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.NotAuthenticated)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    override val currentSession: Session?
        get() = (_sessionState.value as? SessionState.Authenticated)?.session
    override val accessToken: String?
        get() = currentSession?.accessToken
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The scheduled auto-refresh job. Swapped atomically: `scheduleRefresh` is
    // reachable concurrently from `saveSession` (suspend, any dispatcher) and
    // `startAutoRefresh` (any thread), and a plain cancel-then-reassign on a
    // @Volatile ref is a compound op — two interleaved swaps would leak an
    // unreferenced, uncancellable refresh coroutine. getAndSet cancels exactly
    // the job it replaces.
    private val refreshJob = atomic<Job?>(null)

    @Volatile
    private var autoRefreshEnabled: Boolean = autoRefresh

    // Collapses concurrent refreshes (manual call racing the scheduled one, or
    // several 401s at once) onto a single in-flight request. Without this each
    // caller spends the rotating refresh token separately and all but the first
    // fail, forcing a spurious logout.
    private val refreshMutex = Mutex()

    @Volatile
    private var inFlightRefresh: Deferred<SupabaseResult<Session>>? = null

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
        val current =
            currentSession
                ?: return SupabaseResult.Failure(SupabaseError(message = "No active session to refresh"))
        return dedupedRefresh(current)
    }

    override suspend fun restoreSession(): SupabaseResult<Session> {
        _sessionState.value = SessionState.Loading
        val stored = storage.load()
        if (stored == null) {
            _sessionState.value = SessionState.NotAuthenticated
            return SupabaseResult.Failure(SupabaseError(message = "No stored session found"))
        }
        return dedupedRefresh(stored)
    }

    private suspend fun dedupedRefresh(session: Session): SupabaseResult<Session> {
        val deferred =
            refreshMutex.withLock {
                inFlightRefresh ?: scope.async { handleRefresh(session) }.also { inFlightRefresh = it }
            }
        return try {
            deferred.await()
        } finally {
            refreshMutex.withLock {
                if (inFlightRefresh === deferred) inFlightRefresh = null
            }
        }
    }

    override suspend fun initialize(): SupabaseResult<Session> =
        restoreSession()

    override fun startAutoRefresh() {
        autoRefreshEnabled = true
        currentSession?.let(::scheduleRefresh)
    }

    override fun stopAutoRefresh() {
        autoRefreshEnabled = false
        cancelRefresh()
    }

    override fun dispose() {
        close()
    }

    override fun close() {
        cancelRefresh()
        scope.cancel()
    }

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
        if (!autoRefreshEnabled) return
        val delayMs = computeRefreshDelayMs(session)
        val job =
            scope.launch {
                delay(delayMs)
                refreshSession()
            }
        refreshJob.getAndSet(job)?.cancel()
    }

    private fun computeRefreshDelayMs(session: Session): Long {
        val remainingSeconds = remainingSecondsUntilExpiry(session)
        // A session restored from storage may already be past its scheduled
        // refresh (or expired); refresh promptly, but never with a zero delay
        // that would spin the loop if the next token is also short-lived.
        if (remainingSeconds <= 0) return MIN_REFRESH_DELAY_MS
        // Cap the buffer at half the remaining lifetime so a token whose life is
        // shorter than refreshBufferSeconds still schedules a positive delay
        // instead of refreshing immediately in a tight loop. For normal,
        // long-lived tokens this is exactly refreshBufferSeconds.
        val effectiveBuffer = minOf(refreshBufferSeconds, remainingSeconds / 2)
        val secondsUntilRefresh = remainingSeconds - effectiveBuffer
        // A garbage `exp` near Long.MAX would overflow `* 1000` to a negative value,
        // collapse to MIN_REFRESH_DELAY_MS, and busy-refresh forever. Clamp the
        // seconds so the multiply can't overflow; a far-future expiry just yields a
        // far-future (effectively "never within this process") delay, not a storm.
        val cappedSeconds = secondsUntilRefresh.coerceAtMost(MAX_REFRESH_DELAY_SECONDS)
        return maxOf(cappedSeconds * 1000L, MIN_REFRESH_DELAY_MS)
    }

    // Prefer the access token's absolute `exp` claim: a persisted session
    // carries a relative `expires_in` captured at issue time, so scheduling off
    // it after the clock has advanced refreshes too late (or never). Fall back
    // to `expires_in` when the token is not a decodable JWT.
    private fun remainingSecondsUntilExpiry(session: Session): Long {
        val expiryEpochSeconds = accessTokenExpiryEpochSeconds(session.accessToken)
        return if (expiryEpochSeconds != null) {
            expiryEpochSeconds - Clock.System.now().epochSeconds
        } else {
            session.expiresIn
        }
    }

    private fun cancelRefresh() {
        refreshJob.getAndSet(null)?.cancel()
    }

    private companion object {
        // Lower bound on the scheduled delay. Prevents a 0 ms delay from busy-
        // looping the refresh coroutine when a token is already past its refresh
        // window, while still refreshing quickly.
        private const val MIN_REFRESH_DELAY_MS = 1_000L

        // Upper bound (seconds) on the scheduled delay, chosen so `* 1000L` stays
        // well inside Long range regardless of a malformed `exp` claim.
        private const val MAX_REFRESH_DELAY_SECONDS = Long.MAX_VALUE / 1000L
    }
}
