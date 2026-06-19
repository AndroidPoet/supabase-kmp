# Auth (GoTrue) — SDK Coverage Matrix

**Source of truth:** [`contracts/auth.openapi.yaml`](./auth.openapi.yaml) — the official Supabase
Auth REST OpenAPI spec (`info.version: latest`), pinned into this repo.

**SDK audited:** `supabase-auth` + `supabase-auth-admin` (`commonMain`).

**Enforcement:** [`coverage-manifest.yaml`](./coverage-manifest.yaml) is the machine-readable
companion to this doc — it classifies every operation in the pinned spec. CI
([`spec-drift.yml`](../.github/workflows/spec-drift.yml)) runs
[`check_coverage.py`](./check_coverage.py) on every contract/auth change (fails if the spec
gains an unclassified operation) and weekly diffs the pinned spec against upstream with
`oasdiff`. Bump the pinned spec → the coverage job forces you to re-classify any new endpoints.

**Method:** every endpoint's parameters were checked for **placement** (path / query /
header / body) and **wire name** against the spec, then mapped to the SDK call that emits
them. Status legend:

| Status | Meaning |
|---|---|
| ✅ | Covered — same placement, same wire name |
| ⚠️ | Off-contract — wrong placement/name/verb/enum, **or** a spec-vs-server discrepancy |
| ❌ | Missing — spec defines it, SDK doesn't |
| 🚫 | Intentionally out of scope for a thin client SDK (browser/IdP/server-role endpoints) |

> ### ⚠️ The spec is not the server
> The OpenAPI is a **coverage checklist, not ground truth for behavior.** GoTrue's spec
> lags/diverges from the running server in several places. The clearest case:
> **`/reauthenticate` is documented `POST` but the live route is `GET`** (confirmed against
> GoTrue source) — our SDK's `GET` is correct. Anything in the **"verify before changing"**
> bucket below must be confirmed against a real instance (the e2e harness) before we touch
> it; blindly conforming to the spec would *break* working calls.

## Coverage summary

| Slice | Endpoints | ✅ Fully covered | Partial / gaps | Out of scope |
|---|---|---|---|---|
| Core user flows | 27 | 18 | 7 | 5 (callback ×2, saml ×2, magiclink) |
| Admin | 15 | 11 | 4 | — |
| OAuth-server / DCR / custom-providers | 16 | 13 | 1 | 2 |

Most of the surface is correctly wired (incl. PKCE on signup/otp/recover added 2025-06, the
admin OAuth-client + custom-provider CRUD, and the user consent trio). The actionable work
is the small gap list below.

---

## A. Safe additive gaps — ✅ all implemented (backward-compatible)

These were real spec fields/endpoints the SDK didn't emit. **A1–A11 have all landed** as
optional params / new methods (no public API broken; API baselines regenerated additively).
Kept here as the audit record.

| # | Endpoint | Gap | Fix |
|---|---|---|---|
| A1 | `POST /sso` | `skip_http_redirect`, `code_challenge`, `code_challenge_method`, `gotrue_meta_security` not modelled. Without `skip_http_redirect=true` the server **303-redirects instead of returning the JSON `{url}`** that `retrieveSsoUrl` tries to decode — likely a real functional break. | Add fields to `SsoRequest`; default `skip_http_redirect=true` for the URL-returning call; thread PKCE + captcha. |
| A2 | `POST /signup` | `channel` (`sms`/`whatsapp`) missing for phone signup. | Add `channel: String?` to `SignUpRequest` + signUp params. |
| A3 | `PUT /user` | `channel` missing (phone-change delivery). `app_metadata` also absent (but usually admin-only; low priority). | Add `channel: String?` to `UserUpdateRequest`. |
| A4 | `POST /token` (id_token grant) | `client_id`, `issuer` missing. `issuer` is required to verify custom OIDC (e.g. Azure). | Add `clientId`/`issuer` to `IdTokenRequest` + `signInWithIdToken`. |
| A5 | `GET /user/identities/authorize` | `code_challenge`/`code_challenge_method` not emitted — `linkIdentity` has no PKCE, unlike `getOAuthSignInUrl`. | Accept `pkceParams` and append the challenge (mirror the OAuth URL builder). |
| A6 | `POST /verify` | `redirect_to` not modelled. | Add `redirectTo` (query param, consistent with other flows). |
| A7 | `POST /factors/{id}/verify` | `webauthn` object (`{type, credential_response}`) missing — WebAuthn MFA verify can't complete via this method. | Add optional `webauthn` field. (Niche; the passkey module covers the ceremony.) |
| A8 | `GET /admin/audit` | Entire endpoint absent (audit-log listing, `page`/`per_page`). | Add `auditLogEvents(page?, perPage?)` + `AuditLogEntry` model (bare array). |
| A9 | `PUT /admin/users/{id}/factors/{factorId}` | Update-factor absent (only DELETE exists). | Add `updateFactor(userId, factorId, request)` → full `MfaFactor`. |
| A10 | `POST /resend` | `type` accepts the full `OtpType`; `/resend` only allows `signup\|email_change\|sms\|phone_change`. | Validate/narrow (or document). Minor — server rejects invalid today. |
| A11 | `GET /authorize` | `invite_token` only reachable via the free-form `queryParams` map. | Optional typed `inviteToken`. Low priority. |

## B. Spec-vs-server discrepancies — VERIFY against a live instance before changing

Do **not** blind-change these. In each case the SDK likely matches the **real server** and the
OpenAPI is incomplete/wrong. Confirm with the e2e harness first.

| # | Endpoint | Spec says | SDK does | Likely reality |
|---|---|---|---|---|
| B1 | `/reauthenticate` | `POST` | `GET` | **SDK correct** — live route is `GET` (confirmed in GoTrue source). Spec is wrong. **Do not change.** |
| B2 | `POST /invite` | no `redirect_to` | sends `?redirect_to=` (query) | GoTrue reads referrer redirect (like signup/recover); spec under-documents. Keep. |
| B3 | `DELETE /admin/users/{id}` | no request body | sends `{should_soft_delete}` | `should_soft_delete` is a real GoTrue feature; spec omits it. Keep. |
| B4 | `POST /admin/generate_link` | flat response | decodes nested `{properties, user}` | GoTrue historically returns the nested shape. Verify; likely keep. |
| B5 | `GET /admin/users/{id}/factors` | bare array | decodes `{factors:[...]}` | GoTrue historically wraps in `{factors}`. Verify; make tolerant of both if unsure. |
| B6 | `GET /authorize`, `GET /user/identities/authorize` | `scopes` required | omits when empty | Scopes are optional in practice (providers default). Pragmatic; keep. |

## C. Out of scope for a thin client SDK (correctly absent)

- `GET/POST /callback`, `GET /saml/metadata`, `POST /saml/acs` — browser/IdP redirect targets.
- `POST /magiclink` — legacy; the modern equivalent (`POST /otp` with an email) is fully covered.
- `POST /oauth/token`, `GET /oauth/authorize` — the *server/provider* role (third-party clients
  authenticate *against* a Supabase auth server); not the embedding app's job.
- `POST /oauth/clients/register` (Dynamic Client Registration) — experimental, gated behind
  `GOTRUE_OAUTH_SERVER_ALLOW_DYNAMIC_REGISTRATION`; add only on real demand.

## D. Harmless off-contract extras (no action)

`current_password` on `PUT /user`, `factor_id` in the factor-verify body, `code_challenge` in
the `/authorize` URL — all accepted by GoTrue; the `redirect_to` dead field on `ResendOtpRequest`
(now sent as a query param). The `type:"saml"` always-sent on the SSO-provider PUT update is
off-contract but harmless.

---

_Generated from the pinned OpenAPI spec. Re-run the coverage audit whenever the pinned spec is
bumped. The "B" rows are the ones a real-instance e2e pass should settle definitively._
