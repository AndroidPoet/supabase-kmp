# Authentication Guide

## Supported flows

- Email/password sign-up + sign-in
- Phone sign-in
- OTP send + verify
- OAuth URL flow
- PKCE authorization code exchange
- MFA (TOTP, phone)

## Email/password sign-in example

```kotlin
val auth: AuthClient by inject()
val sessionManager: SessionManager by inject()

val result = auth.signInWithEmail(
    email = "user@example.com",
    password = "super-secure-password",
)

result.onSuccess { session ->
    sessionManager.saveSession(session)
}.onFailure { error ->
    println("Sign-in failed: ${error.message}")
}
```

## OAuth + PKCE example

```kotlin
val pkce = auth.generatePkceParams()

val oauthUrl = auth.getOAuthSignInUrl(
    provider = OAuthProvider.GITHUB,
    redirectTo = "myapp://auth/callback",
    codeChallenge = pkce.codeChallenge,
    codeChallengeMethod = pkce.codeChallengeMethod,
)

// Open oauthUrl in browser, then receive callback code
val exchange = auth.exchangeCodeForSession(
    authCode = "code-from-callback",
    codeVerifier = pkce.codeVerifier,
)
```

## Session lifecycle

`SessionManager` is responsible for:

- persisting session tokens
- restoring session at startup
- auto-refresh scheduling
- exposing `SessionState` via `StateFlow`

## Restore on app startup

```kotlin
val restored = sessionManager.restoreSession()

restored.onSuccess {
    println("Session restored")
}.onFailure {
    println("No active session")
}

sessionManager.sessionState.collect { state ->
    when (state) {
        is SessionState.Authenticated -> println("User: ${state.session.user.id}")
        SessionState.NotAuthenticated -> println("Signed out")
        SessionState.Loading -> println("Loading")
        is SessionState.Expired -> println("Expired")
    }
}
```

## Best practices

- Save new session immediately after sign-in
- Restore on app launch
- Never hardcode service-role keys in client apps
- Clear session on explicit sign-out
