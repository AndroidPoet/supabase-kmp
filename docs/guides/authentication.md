# Authentication Guide

## Core flows

- Email/password sign-up and sign-in
- OTP sign-in and verification
- OAuth provider redirect flow
- PKCE code verifier/challenge flow
- MFA enrollment, challenge, and verification

## Session handling

Use `SessionManager` to persist and auto-refresh sessions.
Observe `sessionState` to react to auth lifecycle transitions.
