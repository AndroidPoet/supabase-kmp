# Authentication Guide

## Supported flows

- Email/password sign-up + sign-in
- Phone sign-in
- OTP send + verify
- OAuth URL flow
- PKCE authorization code exchange
- MFA (TOTP, phone)

## Session lifecycle

`SessionManager` is responsible for:

- persisting session tokens
- restoring session at startup
- auto-refresh scheduling
- exposing `SessionState` via `StateFlow`

## Best practices

- Save new session immediately after sign-in
- Restore on app launch
- Never hardcode service-role keys in client apps
- Clear session on explicit sign-out
