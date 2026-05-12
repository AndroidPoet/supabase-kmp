# Security Notes

## Key management

- Do not commit production keys to Git
- Rotate compromised keys immediately
- Scope credentials by environment

## Client-side boundaries

- Never expose service-role key in mobile/web clients
- Use Edge Functions for privileged operations

## Auth hardening

- Prefer PKCE for OAuth
- Enable MFA for sensitive applications
- Expire sessions and refresh aggressively on shared devices
