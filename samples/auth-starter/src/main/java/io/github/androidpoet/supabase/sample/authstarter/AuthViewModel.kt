package io.github.androidpoet.supabase.sample.authstarter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.sample.authstarter.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val status: String = "Not signed in",
    val userId: String? = null,
    val claims: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val signedIn: Boolean get() = userId != null
}

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // Restore a persisted session on launch (no-op with the default in-memory store).
        run("Session restored") { repository.restoreSession() }
    }

    fun setEmail(value: String) = _state.update { it.copy(email = value) }

    fun setPassword(value: String) = _state.update { it.copy(password = value) }

    fun signUp() = run("Signed up") { repository.signUp(state.value.email.trim(), state.value.password) }

    fun signIn() = run("Signed in") { repository.signIn(state.value.email.trim(), state.value.password) }

    fun signInAnonymously() = run("Anonymous session") { repository.signInAnonymously() }

    fun refreshSession() = run("Session refreshed") { repository.refreshSession() }

    fun currentUser() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = repository.currentUserEmail()) {
                is SupabaseResult.Success -> _state.update { it.copy(busy = false, status = "Current user: ${result.value}") }
                is SupabaseResult.Failure -> _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    fun inspectJwt() {
        when (val result = repository.describeJwtClaims()) {
            is SupabaseResult.Success -> _state.update { it.copy(claims = result.value, error = null) }
            is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = repository.signOut()) {
                is SupabaseResult.Success ->
                    _state.update {
                        it.copy(busy = false, status = "Signed out", userId = null, claims = "", password = "")
                    }
                is SupabaseResult.Failure -> _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    /** Shared runner for the actions that produce a [Session]. */
    private fun run(successLabel: String, block: suspend () -> SupabaseResult<Session>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = block()) {
                is SupabaseResult.Success ->
                    _state.update {
                        it.copy(
                            busy = false,
                            status = "$successLabel: ${result.value.user.email ?: result.value.user.id}",
                            userId = result.value.user.id,
                        )
                    }
                is SupabaseResult.Failure ->
                    // Session restore on a cold start legitimately fails — keep it quiet there.
                    _state.update {
                        if (successLabel == "Session restored") {
                            it.copy(busy = false)
                        } else {
                            it.copy(busy = false, error = result.error.message)
                        }
                    }
            }
        }
    }
}

class AuthViewModelFactory(
    private val repository: AuthRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
