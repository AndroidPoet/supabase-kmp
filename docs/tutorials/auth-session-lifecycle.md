# Tutorial: Auth + Session Lifecycle

## Objective

Build a robust startup auth flow with automatic refresh and explicit logout handling.

## Flow

1. Initialize DI and `SessionManager`
2. `restoreSession()` on launch
3. Subscribe to `sessionState`
4. Route app shell based on authenticated vs unauthenticated state
5. On sign-out, call auth sign-out then `clearSession()`

## State handling

- `Loading`: show blocking startup state
- `Authenticated`: continue to app
- `Expired`: attempt refresh + fallback to sign-in
- `NotAuthenticated`: show sign-in
