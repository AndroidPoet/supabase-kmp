# Supabase JavaScript SDK Coverage Map

Source of truth:

- Supabase JavaScript reference start page: https://supabase.com/docs/reference/javascript/start
- Supabase JavaScript reference LLM export: https://supabase.com/llms/js.txt

This file tracks the Supabase JavaScript SDK surface against this Kotlin SDK and the `samples/chat-compose` app. It is a coverage inventory, not copied Supabase documentation.

Status legend:

- `Covered in SDK + sample`: Kotlin SDK exposes the feature and `samples/chat-compose` exercises it.
- `Covered in SDK only`: Kotlin SDK exposes the feature, but the chat sample does not exercise it yet.
- `Partial`: Kotlin SDK exposes related support, but not the full JS surface.
- `Missing from SDK`: JS reference has the feature and this Kotlin SDK does not expose an equivalent.
- `Out of scope for client sample`: Server/admin-only or platform-specific JS behavior that should not be exercised in an anon-key mobile sample.

## Summary

The chat sample now covers the core client-library path: client creation, Auth email/anonymous/session helpers, Database select/insert/RPC/CSV/HEAD, Realtime Postgres changes/broadcast/presence, Storage upload/list/info/download/signed URLs/remove, and Edge Functions invoke/typed invoke.

SDK parity status:

- No tracked JS-reference rows are currently marked `Missing from SDK` or `Partial`.
- Browser/React Native concepts are mapped to Kotlin/KMP equivalents: Ktor engine injection for custom `fetch`, pluggable `SessionStorage` for custom auth storage, and coroutine cancellation for `AbortSignal`.
- Platform ceremonies are covered at the Supabase API boundary: OAuth returns provider URL, Web3 signs in with a caller-provided signed message, and passkeys expose start/verify endpoints. Browser redirects, wallet prompts, and Android/iOS passkey prompts remain platform/app responsibilities.
- Auth admin uses a separate service-role module. OAuth authorization server APIs are exposed in the auth module for server/consent-page flows.
- Storage vector buckets, analytics bucket management, and the Iceberg REST catalog wrapper are covered in the SDK.

## Client Initialization

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `createClient` / initializing | `Supabase.create` | Yes | Covered in SDK + sample | `MainActivity` creates one client with URL/key. |
| custom domain | `Supabase.create(projectUrl = ...)` | Indirect | Covered in SDK only | Any URL works; not a separate API. |
| custom schema option | `DatabaseClient` methods accept `schema` | No | Covered in SDK only | Sample uses public schema only. |
| custom fetch implementation | `Supabase.create(engineFactory = ...)` | No | Covered in SDK only | Kotlin equivalent is custom Ktor engine injection instead of JS `fetch`. |
| global custom headers | `SupabaseConfigBuilder.headers` | No | Covered in SDK only | Applied by `HttpTransport` default request headers; per-call headers can override matching keys. |
| React Native storage options | `SessionStorage` / `SessionManager` | No | Covered differently | Kotlin uses pluggable `SessionStorage` instead of JS/RN storage option names. |

## Database

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `from` | table name parameters on `DatabaseClient` | Yes | Covered in SDK + sample | Sample uses `chat_rooms` and `chat_messages`. |
| `select` | `select`, `selectTyped` | Yes | Covered in SDK + sample | Sample loads rooms/messages. |
| `insert` | `insert`, `insertTyped`, `insertTypedMany` | Yes | Covered in SDK + sample | Sample creates rooms and messages. |
| `update` | `update`, `updateTyped`, `updateUnit` | No | Covered in SDK only | Add a sample action to edit a message or room. |
| `upsert` | `upsertTyped`, `upsertTypedMany` | No | Covered in SDK only | Good candidate: upsert user display/profile row. |
| `delete` | `delete`, `deleteTyped`, `deleteUnit` | No | Covered in SDK only | Good candidate: delete own smoke message. |
| `rpc` | `rpc`, `rpcTyped`, `rpcGet*` helpers | Yes | Covered in SDK + sample | Sample uses `rpcGetSingleTyped`. |
| `schema` | `schema` args on database calls | No | Covered in SDK only | Sample only uses `public`. |
| `returns` / `overrideTypes` | Kotlin serialization generic return types | Yes | Covered in SDK + sample | Kotlin equivalent is static typed decode. |
| `throwOnError` | `SupabaseResult.getOrThrow` | No | Covered in SDK only | Sample uses explicit result handling. |
| `setHeader` | `headers` parameter on database calls | No | Covered in SDK only | Per-call custom headers are merged with generated PostgREST headers; generated schema/content/accept headers win conflicts. |
| `abortSignal` | coroutine cancellation of suspend calls | No | Covered differently | Kotlin cancellation is the KMP equivalent; transport propagates `CancellationException`. |
| `csv` | `selectCsv`, `rpcCsv`, `rpcGetCsv` | Yes | Covered in SDK + sample | Sample uses `selectCsv`. |
| `explain` | `ExplainOptions` on select/update/delete/rpc/rpcGet | No | Covered in SDK only | Sends PostgREST plan `Accept` header. |
| `limit` | `FilterBuilder.limit` | Yes | Covered in SDK + sample | Sample paginates messages. |
| `range` | `FilterBuilder.range` | No | Covered in SDK only | Sample uses `lt` + `limit`, not range. |
| `order` | `FilterBuilder.order` | Yes | Covered in SDK + sample | Sample orders rooms/messages. |
| `single` | `selectSingleTyped`, `rpcSingleTyped` | No | Covered in SDK only | Sample does not call single select. |
| `maybeSingle` | `selectMaybeSingleTyped`, `rpcMaybeSingleTyped` | No | Covered in SDK only | Sample does not call maybe-single. |
| `maxAffected`, `rollback`, `stripNulls` | database request options | No | Covered in SDK only | `maxAffected` is limited to update/delete/rpc; `rollback` to write/RPC calls. |
| `retry` | `retry` option on GET-based database calls | No | Covered in SDK only | Scoped to PostgREST read calls; retries transient network errors and 503/520. |

## Database Filters

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `eq` | `FilterBuilder.eq` | Yes | Covered in SDK + sample | Used for room/message filters. |
| `lt` | `FilterBuilder.lt` | Yes | Covered in SDK + sample | Used for pagination. |
| `neq`, `gt`, `gte`, `lte` | `FilterBuilder` methods | No | Covered in SDK only | Add diagnostics to exercise. |
| `like`, `ilike` | `FilterBuilder.like`, `ilike` | No | Covered in SDK only | Add room search diagnostic. |
| `likeAllOf`, `likeAnyOf`, `ilikeAllOf`, `ilikeAnyOf` | `FilterBuilder` methods | No | Covered in SDK only | Matches JS PostgREST `like/ilike` all/any helpers. |
| `is`, `in`, `match` | `FilterBuilder` methods | No | Covered in SDK only | Not in sample. |
| `contains`, `containedBy`, `overlaps` | `FilterBuilder` methods | No | Covered in SDK only | Requires array/json/range columns. |
| `rangeGt`, `rangeGte`, `rangeLt`, `rangeLte`, `rangeAdjacent` | `FilterBuilder` range methods | No | Covered in SDK only | Requires range columns. |
| `strictlyLeft`, `strictlyRight`, `notExtendLeft`, `notExtendRight` | `FilterBuilder` methods | No | Covered in SDK only | Requires range columns. |
| `not`, `or`, `and` | `FilterBuilder` methods | No | Covered in SDK only | Add diagnostics if needed. |
| `textSearch` | `FilterBuilder.textSearch` | No | Covered in SDK only | Requires text search setup. |
| `filter` | `FilterBuilder.filter` | No | Covered in SDK only | Escape hatch not in sample. |

## Auth User APIs

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `signUp` | `signUpWithEmail`, `signUpWithEmailAndSaveSession` | Yes | Covered in SDK + sample | Auth tab. |
| `signInWithPassword` | `signInWithEmail`, `signInWithEmailAndSaveSession` | Yes | Covered in SDK + sample | Auth tab. |
| `signInAnonymously` | `signInAnonymously`, `signInAnonymouslyAndSaveSession` | Yes | Covered in SDK + sample | Auth tab and local smoke test. |
| `getUser` | `getUser`, `getUserForCurrentSession` | Yes | Covered in SDK + sample | Auth tab. |
| `refreshSession` | `refreshToken`, `refreshCurrentSession`, `SessionManager.refreshSession` | Yes | Covered in SDK + sample | Auth tab. |
| `signOut` | `signOut`, `signOutCurrentSession`, scoped helpers | Yes | Covered in SDK + sample | Auth tab covers current session sign out. |
| `getSession` | `SessionManager.restoreSession` / state | Yes | Covered in SDK + sample | Startup session restore. |
| `setSession` | `SessionManager.saveSession`, import helpers | No | Covered in SDK only | Not in sample. |
| `getClaims` | `parseJwtClaims`, `parseCurrentSessionJwtClaims` | Yes | Covered in SDK + sample | JWT button. |
| `exchangeCodeForSession` | `exchangeCodeForSession`, `exchangeCodeForSessionAndSave` | No | Covered in SDK only | Needs OAuth/PKCE flow. |
| `signInWithOtp`, `verifyOtp`, `resend` | OTP helpers | No | Covered in SDK only | Needs email/phone OTP UI. |
| `signInWithIdToken` | `signInWithIdToken`, save-session helper | No | Covered in SDK only | Needs provider token. |
| `signInWithOAuth` | `signInWithOAuth`, `getOAuthSignInUrl`, import URL/fragment helpers | No | Covered in SDK only | Kotlin returns provider + URL; browser/app redirect is left to platform code. |
| `signInWithSSO` | `retrieveSsoUrl`, `retrieveSsoUrlForCurrentSession` | No | Covered in SDK only | Not in sample. |
| `signInWithWeb3` | `signInWithWeb3` | No | Covered differently | Supabase Web3 grant is covered for Ethereum/Solana; wallet prompting/message construction is platform/app code. |
| `signInWithPasskey`, `registerPasskey` | passkey start/verify authentication and registration methods | No | Covered differently | Supabase passkey endpoints are covered; Android/iOS/browser credential ceremony is platform/app code. |
| passkey management | `passkeyList`, `passkeyUpdate`, `passkeyDelete` | No | Covered in SDK only | Mirrors JS passkey list/update/delete server endpoints. |
| `getUserIdentities` | `getUserIdentities`, `getUserIdentitiesForCurrentSession` | No | Covered in SDK only | Mirrors JS behavior by reading identities from `getUser`. |
| `linkIdentity`, `unlinkIdentity` | link/unlink helpers | No | Covered in SDK only | Not in sample. |
| `updateUser` | `updateUser`, `updateUserForCurrentSession` | No | Covered in SDK only | Add profile update action. |
| `resetPasswordForEmail` | `resetPasswordForEmail`, `forgotPassword` | No | Covered in SDK only | Not in sample. |
| `reauthenticate` | `reauthenticate`, `reauthenticateCurrentSession` | No | Covered in SDK only | Not in sample. |
| `onAuthStateChange` | `SessionManager.sessionState`, `onAuthStateChange` | No | Covered in SDK only | Kotlin exposes the state flow plus a JS-style callback subscription helper. |
| `startAutoRefresh`, `stopAutoRefresh`, `initialize`, `dispose` | `SessionManager` lifecycle methods | No | Covered in SDK only | Kotlin lifecycle lives on `SessionManager`; `initialize` restores session and `dispose` aliases close. |

## Auth MFA

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `enroll` | `mfaEnroll` | No | Covered in SDK only | Not in sample. |
| `challenge` | `mfaChallenge` | No | Covered in SDK only | Not in sample. |
| `verify` / `challengeAndVerify` | `mfaVerify`, `mfaChallengeAndVerify` | No | Covered in SDK only | Combined helper calls challenge then verify with the returned challenge id. |
| `unenroll` | `mfaUnenroll` | No | Covered in SDK only | Not in sample. |
| `listFactors` | `mfaListFactors` | No | Covered in SDK only | Not in sample. |
| `getAuthenticatorAssuranceLevel` | `mfaGetAuthenticatorAssuranceLevel` | No | Covered in SDK only | Not in sample. |

## Auth Admin, Passkey Admin, OAuth Admin/Server

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| Admin users/generate-link/sign-out APIs | `supabase-auth-admin` `AuthAdminClient` | No | Covered in SDK only | Separate service-role module; `deleteUser` supports `shouldSoftDelete`. Not for anon mobile sample. |
| Admin MFA factor APIs | `AuthAdminClient.listFactors`, `AuthAdminClient.deleteFactor` | No | Covered in SDK only | Separate service-role module; not for anon mobile sample. |
| Admin custom provider APIs | `AuthAdminClient.listCustomProviders`, `createCustomProvider`, `getCustomProvider`, `updateCustomProvider`, `deleteCustomProvider` | No | Covered in SDK only | Separate service-role module; not for anon mobile sample. |
| OAuth client admin APIs | `AuthAdminClient.listOAuthClients`, `createOAuthClient`, `getOAuthClient`, `updateOAuthClient`, `deleteOAuthClient`, `regenerateOAuthClientSecret` | No | Covered in SDK only | Separate service-role module; not for anon mobile sample. |
| Passkey admin APIs | `AuthAdminClient.listPasskeys`, `AuthAdminClient.deletePasskey` | No | Covered in SDK only | Separate service-role module; not for anon mobile sample. |
| OAuth authorization server APIs | `oauthGetAuthorizationDetails`, `oauthApproveAuthorization`, `oauthDenyAuthorization`, `oauthListGrants`, `oauthRevokeGrant` | No | Covered in SDK only | Requires OAuth 2.1 server/consent-page context; not for anon mobile sample. |

## Edge Functions

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `functions.invoke` | `invoke`, `invokeTyped`, `invokeUnit` | Yes | Covered in SDK + sample | Raw and typed invoke covered. |
| invoke with body | `invokeWithBody`, `invokeWithBodyTyped`, `invokeWithBodyUnit` | No | Covered in SDK only | Sample uses `invoke` and `invokeTyped`. |
| `functions.setAuth` | `FunctionsClient.setAuth` | No | Covered in SDK only | Sets functions-client Authorization header; invoke-level headers can still override it, matching JS precedence. |
| `corsHeaders` | Edge function backend helper | No | Out of scope for client sample | Not a client SDK API in this Kotlin repo. |

## Realtime

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `channel` | `RealtimeClient.channel` | Yes | Covered in SDK + sample | Chat subscribes to one channel. |
| `subscribe` | builder `subscribe`, extension subscribe helpers | Yes | Covered in SDK + sample | Chat opens subscription. |
| Postgres changes via `on` | `onPostgresChange`, `subscribeToPostgresChanges` | Yes | Covered in SDK + sample | INSERT covered. |
| Broadcast via `on` / `send` | `onBroadcast`, `broadcast`, `subscribeToBroadcast` | Yes | Covered in SDK + sample | Broadcast button. |
| Presence via `on` / `track` | `onPresence`, `track`, `subscribeToPresence` | Yes | Covered in SDK + sample | Track covered; join/leave/sync flows not displayed. |
| `untrack` | `RealtimeSubscription.untrack` | No | Covered in SDK only | Add presence offline button. |
| `unsubscribe` | `RealtimeSubscription.unsubscribe` | Indirect | Covered in SDK + sample | Used when switching/disconnecting. |
| `connect`, `disconnect` | `RealtimeClient.connect`, `disconnect` | Yes | Covered in SDK + sample | Repository connects/disconnects. |
| `setAuth` | `RealtimeClient.setAuth` | No | Covered in SDK only | Not used by sample after auth changes. |
| `removeChannel`, `removeAllChannels`, `getChannels` | remove/get APIs | No | Covered in SDK only | Sample unsubscribes directly. |
| `connectionState`, `isConnected`, `isConnecting`, `isDisconnecting` | `ConnectionState`, boolean state properties, `awaitConnected`, `awaitDisconnected` | No | Covered in SDK only | Sample does not display state. |
| `presenceState` | `presenceDataFlow`, presence flows | No | Covered in SDK only | Not in sample UI. |
| `send` / channel event send | `RealtimeSubscription.send`, `broadcast`, `track`, `untrack` | Yes | Covered in SDK + sample | Kotlin exposes raw send with constrained `SendType`, plus typed helpers. |
| `push`, `sendHeartbeat`, `onHeartbeat`, `teardown`, `_push`, `Push`, websocket constructor/log utilities | internal transport/session behavior | No | Out of scope / JS internal | Official JS source exposes some low-level methods on `RealtimeClient`, but Kotlin keeps heartbeat, websocket transport, logging, teardown, and push buffering internal. |
| `updateJoinPayload` | builder config before subscribe; `setAuth` for token refresh | No | Covered differently | Kotlin channel join payload is derived from builder config and current auth token instead of mutable post-construction join payload. |
| private channels / broadcast replay | builder config methods | No | Covered in SDK only | `setPrivate`, `configureBroadcastReplay` not sampled. |

## Storage: Buckets and Objects

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| `listBuckets` | `listBuckets` | Yes | Covered in SDK + sample | Inspect storage. |
| `getBucket` | `getBucket` | Yes | Covered in SDK + sample | Inspect storage. |
| `createBucket`, `updateBucket`, `deleteBucket`, `emptyBucket` | bucket management methods | No | Covered in SDK only | Not safe for default sample bucket unless guarded. |
| `from` | bucket/path parameters | Yes | Covered in SDK + sample | Kotlin does not have JS-style bucket builder. |
| `upload` | `upload` | Yes | Covered in SDK + sample | Upload text file. |
| `update` object | `update` | No | Covered in SDK only | Add update-file action if desired. |
| `download` | `download`, `downloadPublic` | Yes | Covered in SDK + sample | Inspect storage downloads. |
| `list` | `list` | Yes | Covered in SDK + sample | List files button and smoke test. |
| `info` | `info`, `infoPublic` | Yes | Covered in SDK + sample | Inspect storage. |
| `exists` | `exists`, `existsPublic` | Yes | Covered in SDK + sample | Inspect storage. |
| `remove` | `remove`, `removeWithResult` | Yes | Covered in SDK + sample | Remove button covers unit path. |
| `move` | `move` | No | Covered in SDK only | Not in sample. |
| `copy` | `copy` | No | Covered in SDK only | Not in sample. |
| `createSignedUrl`, `createSignedUrls` | signed URL methods | Yes | Covered in SDK + sample | Single and path batch covered. |
| `createSignedUploadUrl`, `uploadToSignedUrl` | signed upload methods | No | Covered in SDK only | Not in sample. |
| `getPublicUrl` | `getPublicUrl`, URL batch helpers | Yes | Covered in SDK + sample | URLs/Inspect cover public URL. |
| authenticated URLs | `getAuthenticatedUrl`, URL batch helpers | Yes | Covered in SDK + sample | Inspect storage. |
| render/image transform URLs | render URL helpers | No | Covered in SDK only | Requires image object; we intentionally did not upload images. |
| `listV2` | `StorageClient.listV2` | No | Covered in SDK only | Returns V2 objects/folders pagination result. |

## Storage: Analytics and Vector Buckets

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| Analytics Buckets | `createAnalyticsBucket`, `listAnalyticsBuckets`, `deleteAnalyticsBucket`, `analyticsCatalog` | No | Covered in SDK only | `analyticsCatalog(bucketName)` exposes the Iceberg REST catalog namespace/table operations with raw JSON requests/responses. |
| Vector Buckets: bucket/index/vector CRUD and query APIs | `createVectorBucket`, `getVectorBucket`, `listVectorBuckets`, `deleteVectorBucket`, `createVectorIndex`, `getVectorIndex`, `listVectorIndexes`, `deleteVectorIndex`, `putVectors`, `getVectors`, `listVectors`, `queryVectors`, `deleteVectors` | No | Covered in SDK only | Alpha JS vector API mapped to flat Kotlin methods under `StorageClient`. |

## Core Result and Error Handling

| JS reference item | Kotlin SDK equivalent | Sample coverage | Status | Notes |
|---|---|---:|---|---|
| JS response `{ data, error }` | `SupabaseResult.Success` / `Failure` | Yes | Covered in SDK + sample | Sample handles both branches throughout. |
| throw-style handling | `getOrThrow`, `toKotlinResult` | No | Covered in SDK only | Sample uses explicit result handling. |
| error helpers/categories | `SupabaseErrorCategory`, helper predicates | No | Covered in SDK only | Not displayed in sample. |

## Sample Coverage Next Steps

If the target is 100% coverage of existing Kotlin SDK APIs in the sample, add these in order:

1. Database diagnostics: `updateTyped`, `upsertTyped`, `deleteTyped`, `selectSingleTyped`, `selectMaybeSingleTyped`, `rpcHead`, `rpcCsv`, range/or/and/text-search filters.
2. Storage diagnostics: object `update`, `move`, `copy`, `removeWithResult`, signed upload URL, upload-to-signed URL, public download/info/exists variants.
3. Realtime diagnostics: show active channels, connection state, `setAuth`, presence sync/join/leave flows, `untrack`, remove channel/all channels.
4. Auth diagnostics: update user, password reset, reauthenticate, OTP send/verify, phone auth, SSO URL, OAuth URL/fragment import, identity link/unlink.
5. Functions diagnostics: `invokeWithBody`, `invokeWithBodyTyped`, `invokeUnit`, `invokeWithBodyUnit`.

Optional SDK additions beyond current JS-reference parity:

1. Auth: optional Android/iOS adapters for Web3 wallets and passkey credential ceremonies if we choose to ship platform UI helpers.
2. Storage: typed Iceberg schema/table models if we want stronger Kotlin types over the raw JSON catalog API.
3. Realtime: optional heartbeat diagnostics only if we intentionally want JS low-level control; current Kotlin SDK covers the user-facing channel flows and keeps JS `Push`/transport internals out of scope.
