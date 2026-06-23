package io.github.androidpoet.supabase.sample.authstarter.data

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.getUserForCurrentSession
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.parseCurrentSessionJwtClaims
import io.github.androidpoet.supabase.auth.refreshCurrentSession
import io.github.androidpoet.supabase.auth.session.SessionManager
import io.github.androidpoet.supabase.auth.signInAnonymouslyAndSaveSession
import io.github.androidpoet.supabase.auth.signInWithEmailAndSaveSession
import io.github.androidpoet.supabase.auth.signOutCurrentSession
import io.github.androidpoet.supabase.auth.signUpWithEmailAndSaveSession
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map

/**
 * Thin wrapper over the supabase-kmp auth surface. Everything returns a
 * [SupabaseResult], so callers branch on Success/Failure instead of catching.
 *
 * The [SessionManager] persists the session through pluggable storage (BYO) —
 * this sample uses the default in-memory store, so a real app would inject its
 * own (DataStore / Keychain / etc.) when creating the session manager.
 */
class AuthRepository(
    private val auth: AuthClient,
    private val sessionManager: SessionManager,
) {
    suspend fun restoreSession(): SupabaseResult<Session> =
        sessionManager.restoreSession()

    suspend fun signUp(email: String, password: String): SupabaseResult<Session> =
        auth.signUpWithEmailAndSaveSession(sessionManager, email, password)

    suspend fun signIn(email: String, password: String): SupabaseResult<Session> =
        auth.signInWithEmailAndSaveSession(sessionManager, email, password)

    suspend fun signInAnonymously(): SupabaseResult<Session> =
        auth.signInAnonymouslyAndSaveSession(sessionManager)

    suspend fun refreshSession(): SupabaseResult<Session> =
        auth.refreshCurrentSession(sessionManager)

    suspend fun currentUserEmail(): SupabaseResult<String> =
        auth.getUserForCurrentSession(sessionManager).map { it.email ?: it.id }

    suspend fun signOut(): SupabaseResult<Unit> =
        auth.signOutCurrentSession(sessionManager)

    fun describeJwtClaims(): SupabaseResult<String> =
        sessionManager.parseCurrentSessionJwtClaims().map { claims ->
            listOf("sub", "role", "exp", "iss", "email")
                .mapNotNull { key -> claims[key]?.let { "$key=$it" } }
                .joinToString(separator = "\n")
                .ifBlank { "Session has no decodable JWT claims" }
        }
}
