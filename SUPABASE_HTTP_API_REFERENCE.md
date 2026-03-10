# Supabase Raw HTTP API Reference

Scraped from official Supabase and PostgREST documentation. This covers the raw HTTP layer
needed to build a KMP SDK from scratch.

---

## Table of Contents

1. [Base URLs & Authentication](#1-base-urls--authentication)
2. [PostgREST / Database API](#2-postgrest--database-api)
3. [Auth (GoTrue) API](#3-auth-gotrue-api)
4. [Realtime WebSocket Protocol](#4-realtime-websocket-protocol)
5. [Storage API](#5-storage-api)
6. [Edge Functions](#6-edge-functions)
7. [Error Responses & Rate Limiting](#7-error-responses--rate-limiting)

---

## 1. Base URLs & Authentication

### URL Patterns

| Service       | URL Pattern                                                        |
|---------------|--------------------------------------------------------------------|
| REST API      | `https://<PROJECT_REF>.supabase.co/rest/v1/`                       |
| Auth          | `https://<PROJECT_REF>.supabase.co/auth/v1/`                       |
| Storage       | `https://<PROJECT_REF>.supabase.co/storage/v1/`                    |
| Realtime WS   | `wss://<PROJECT_REF>.supabase.co/realtime/v1/websocket`            |
| Realtime REST | `https://<PROJECT_REF>.supabase.co/realtime/v1/api/broadcast`      |
| Edge Functions| `https://<PROJECT_REF>.supabase.co/functions/v1/<function-name>`   |
| JWKS          | `https://<PROJECT_REF>.supabase.co/auth/v1/.well-known/jwks.json` |
| TUS Upload    | `https://<PROJECT_REF>.storage.supabase.co/storage/v1/upload/resumable` |

### Authentication Headers

Every request requires two headers:

```
apikey: <SUPABASE_ANON_KEY>
Authorization: Bearer <JWT_ACCESS_TOKEN>
```

- `apikey` header: Always the project's publishable anon key (or service_role key for admin ops)
- `Authorization`: Bearer token - either the anon key (for unauthenticated access) or a user's access token after sign-in

### Key Types

| Key            | Role              | Use Case                                    |
|----------------|-------------------|---------------------------------------------|
| `anon`         | `anon` Postgres role | Client-side, RLS-protected requests        |
| `service_role` | `service_role`      | Server-side admin, bypasses RLS            |

Both are long-lived JWTs (10-year expiry for legacy keys).

### JWT Structure

Three parts: `<header>.<payload>.<signature>` (Base64-URL encoded)

**Payload claims:**

| Claim  | Purpose                                    |
|--------|--------------------------------------------|
| `iss`  | Issuer URL (append `/.well-known/jwks.json` for public keys) |
| `sub`  | User ID (UUID)                             |
| `role` | Postgres role (`anon`, `authenticated`, `service_role`) |
| `exp`  | Expiration timestamp                       |
| `session_id` | UUID correlating to `auth.sessions` table |

**Validation endpoints:**
- Supabase-issued: `GET /auth/v1/user` (returns 200 if valid)
- Third-party: Verify against JWKS at `/auth/v1/.well-known/jwks.json` (edge-cached 10 min)

---

## 2. PostgREST / Database API

Base path: `/rest/v1/<table_name>`

### CRUD Operations

#### Read Rows
```
GET /rest/v1/<table>?select=<columns>&<filters>&order=<ordering>&limit=<n>&offset=<n>
Headers:
  apikey: <KEY>
  Authorization: Bearer <TOKEN>
```

#### Insert Rows
```
POST /rest/v1/<table>
Headers:
  apikey: <KEY>
  Authorization: Bearer <TOKEN>
  Content-Type: application/json
  Prefer: return=representation    (to get inserted rows back)
Body: { "col1": "val1", "col2": "val2" }
      or [{ ... }, { ... }]        (bulk insert)
```

#### Upsert
```
POST /rest/v1/<table>
Headers:
  Prefer: resolution=merge-duplicates
  (or: Prefer: resolution=ignore-duplicates)
Body: [{ "id": 1, "name": "updated" }]
```

To specify conflict column: `POST /rest/v1/<table>?on_conflict=<column>`

#### Single-Row Upsert
```
PUT /rest/v1/<table>?<primary_key_filter>
Headers:
  Content-Type: application/json
Body: { full row }
```

#### Update Rows
```
PATCH /rest/v1/<table>?<filters>
Headers:
  Content-Type: application/json
  Prefer: return=representation
Body: { "col": "new_value" }
```

#### Delete Rows
```
DELETE /rest/v1/<table>?<filters>
Headers:
  Prefer: return=representation    (optional, to get deleted rows back)
```

### Vertical Filtering (Column Selection)

Via `select` query parameter:

| Syntax                          | Effect                              |
|---------------------------------|-------------------------------------|
| `select=*`                      | All columns (default)               |
| `select=col1,col2`             | Specific columns                    |
| `select=alias:col`             | Rename column in response           |
| `select=col::text`             | Cast type                           |
| `select=json_col->>key`        | Extract JSON as text                |
| `select=json_col->key`         | Extract JSON as JSON                |
| `select=arr_col->0`            | Array element access                |

### Horizontal Filtering (WHERE)

Filters as query parameters: `?column=operator.value`

| Operator | SQL Equivalent | Example                         |
|----------|---------------|----------------------------------|
| `eq`     | `=`           | `?id=eq.5`                       |
| `neq`    | `<>` / `!=`   | `?status=neq.inactive`           |
| `gt`     | `>`           | `?age=gt.18`                     |
| `gte`    | `>=`          | `?age=gte.18`                    |
| `lt`     | `<`           | `?age=lt.65`                     |
| `lte`    | `<=`          | `?age=lte.65`                    |
| `like`   | `LIKE`        | `?name=like.J*` (use `*` for `%`) |
| `ilike`  | `ILIKE`       | `?name=ilike.*john*`             |
| `is`     | `IS`          | `?active=is.true` / `is.null`    |
| `in`     | `IN`          | `?id=in.(1,2,3)`                 |
| `cs`     | `@>` (contains)| `?tags=cs.{new}`                |
| `cd`     | `<@` (contained by)| `?tags=cd.{new,old}`        |
| `ov`     | `&&` (overlap) | `?period=ov.[2020-01-01,2020-06-30]` |
| `sl`     | `<<` (strictly left) | `?range=sl.(1,10)`        |
| `sr`     | `>>` (strictly right)| `?range=sr.(1,10)`        |
| `adj`    | `-\|-` (adjacent) | `?range=adj.(1,10)`          |
| `fts`    | `@@` (full-text)| `?text=fts(english).term`       |
| `plfts`  | plain FTS     | `?text=plfts.term`               |
| `phfts`  | phrase FTS    | `?text=phfts.term`               |
| `wfts`   | websearch FTS | `?text=wfts.term`                |
| `not`    | negate        | `?col=not.eq.5`                  |

**Logical operators:**
- Default between parameters: AND
- OR: `?or=(col1.eq.val1,col2.eq.val2)`
- Pattern matching on single column: `?col=like(any).{pat1,pat2}` (OR), `like(all).{pat1,pat2}` (AND)

### Ordering

```
?order=col1.asc,col2.desc
?order=col.nullsfirst
?order=col.nullslast
?order=json_col->>key.asc
```

### Pagination

**Range header:**
```
Range: rows=0-9
Response: Content-Range: 0-9/100
```

**Query parameters:**
```
?limit=10&offset=20
```

### Resource Embedding (Joins)

Via the `select` parameter:

```
# Many-to-one (returns object)
?select=title,directors(id,last_name)

# One-to-many (returns array)
?select=last_name,films(title)

# Nested embedding
?select=roles(character,films(title,year))

# Disambiguation with multiple FKs
?select=name,billing_address:addresses!billing_fk(name)

# Inner join (filter top-level by embedded)
?select=title,actors!inner(first_name)&actors.first_name=eq.Jehanne

# Filter on embedded resource (without filtering top-level)
?select=*,actors(*)&actors.order=last_name&actors.limit=10

# Spread operator (flatten embedded into parent)
?select=title,...directors(director_name:first_name)

# Empty embed (filter only, don't return)
?select=title,actors()&actors.first_name=eq.Jehanne

# Null filter on relationship existence
?select=title,actors(*)&actors=not.is.null
```

### RPC (Stored Procedures / Functions)

```
POST /rest/v1/rpc/<function_name>
Headers:
  Content-Type: application/json
Body: { "param1": "value1", "param2": "value2" }
```

- GET allowed for immutable functions: `GET /rest/v1/rpc/<fn>?param1=value1`
- Array params: `{ "arr": [1,2,3,4] }`
- Binary input: `Content-Type: application/octet-stream` (single `bytea` param)
- XML input: `Content-Type: text/xml` (single `xml` param)
- Table-returning functions support all read filters, ordering, pagination
- Scalar functions return the value directly

### Response Control Headers

| Header                             | Effect                                |
|------------------------------------|---------------------------------------|
| `Prefer: return=representation`    | Return affected rows in response body |
| `Prefer: return=minimal`          | Return no body (204)                  |
| `Prefer: return=headers-only`     | Return only headers                   |
| `Prefer: count=exact`             | Include exact count in Content-Range  |
| `Accept: application/vnd.pgrst.plan` | Return query execution plan        |

### Supported Content Types for Insert

| Content-Type                          | Format       |
|---------------------------------------|-------------|
| `application/json`                    | JSON object/array |
| `text/csv`                            | CSV with header row |
| `application/x-www-form-urlencoded`  | Form data    |

---

## 3. Auth (GoTrue) API

Base path: `/auth/v1`

### Public Endpoints (no Bearer token needed, only apikey)

#### Sign Up
```
POST /auth/v1/signup
Headers:
  apikey: <KEY>
  Content-Type: application/json
Body: {
  "email": "user@example.com",
  "password": "securepassword",
  "data": { "custom_field": "value" }   // optional user_metadata
}
Response 200: {
  "id": "uuid",
  "email": "user@example.com",
  "role": "authenticated",
  "app_metadata": {},
  "user_metadata": {},
  "identities": [],
  "created_at": "timestamp",
  "confirmed_at": "timestamp"
}
```

Phone signup: use `"phone": "+1234567890"` instead of `"email"`.

#### Sign In with Password
```
POST /auth/v1/token?grant_type=password
Headers:
  apikey: <KEY>
  Content-Type: application/json
Body: {
  "email": "user@example.com",
  "password": "securepassword"
}
Response 200: {
  "access_token": "eyJ...",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "v1.abc...",
  "user": { ... }
}
```

Phone sign-in: use `"phone"` instead of `"email"`.

#### Refresh Token
```
POST /auth/v1/token?grant_type=refresh_token
Headers:
  apikey: <KEY>
  Content-Type: application/json
Body: {
  "refresh_token": "v1.abc..."
}
Response 200: {
  "access_token": "eyJ...",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "v1.xyz...",
  "user": { ... }
}
```

**Refresh token rules:**
- Refresh tokens are single-use (rotation)
- 10-second reuse window for legitimate retries (SSR, network)
- Reuse outside window revokes entire session

#### Passwordless OTP
```
POST /auth/v1/otp
Headers:
  apikey: <KEY>
  Content-Type: application/json
Body: {
  "email": "user@example.com"    // or "phone"
}
```

#### Password Recovery
```
POST /auth/v1/recover
Headers:
  apikey: <KEY>
  Content-Type: application/json
Body: {
  "email": "user@example.com"
}
```

#### Verify (confirm signup, recovery, OTP)
```
POST /auth/v1/verify
Headers:
  apikey: <KEY>
  Content-Type: application/json
Body: {
  "type": "signup",        // or "recovery", "magiclink", "sms"
  "token": "123456"
}
```

#### OAuth Sign-In
```
GET /auth/v1/authorize?provider=<provider>&redirect_to=<url>&scopes=<scopes>
```

The server returns a redirect (302) to the provider's authorization page.
After authorization, provider redirects to callback:

```
GET /auth/v1/callback?code=<auth_code>&...
```

The callback exchanges the code and redirects to your `redirect_to` URL with tokens in the fragment or query.

**Supported providers:** Google, Facebook, Apple, Azure, Twitter, GitHub, GitLab, Bitbucket, Discord, Figma, Kakao, Keycloak, LinkedIn, Notion, Slack, Spotify, Twitch, WorkOS, Zoom (20+)

### Authenticated Endpoints (require Bearer token)

#### Get Current User
```
GET /auth/v1/user
Headers:
  apikey: <KEY>
  Authorization: Bearer <ACCESS_TOKEN>
Response 200: { user object }
```

#### Update User
```
PUT /auth/v1/user
Headers:
  apikey: <KEY>
  Authorization: Bearer <ACCESS_TOKEN>
  Content-Type: application/json
Body: {
  "email": "new@email.com",
  "password": "newpassword",
  "data": { "key": "value" }
}
```

#### Sign Out
```
POST /auth/v1/logout
Headers:
  apikey: <KEY>
  Authorization: Bearer <ACCESS_TOKEN>
```

### Admin Endpoints (require service_role key)

| Method | Path                        | Purpose                    |
|--------|-----------------------------|----------------------------|
| GET    | `/auth/v1/admin/users`      | List all users             |
| POST   | `/auth/v1/admin/users`      | Create user                |
| GET    | `/auth/v1/admin/user/{id}`  | Get user by ID             |
| PUT    | `/auth/v1/admin/user/{id}`  | Update user                |
| DELETE | `/auth/v1/admin/user/{id}`  | Delete user                |
| POST   | `/auth/v1/admin/generate_link` | Generate email action link |
| POST   | `/auth/v1/invite`           | Send invitation            |

### System Endpoints

| Method | Path                | Purpose              |
|--------|---------------------|----------------------|
| GET    | `/auth/v1/health`   | Health check         |
| GET    | `/auth/v1/settings` | Server configuration |

### Session Lifecycle

- Access tokens: short-lived (5 min to 1 hour, configurable)
- Refresh tokens: never expire, single-use with rotation
- Sessions contain `session_id` claim in JWT
- Expired sessions cleaned from DB 24 hours after expiry
- Two flows: **Implicit** (tokens in URL fragment) and **PKCE** (code exchange)

---

## 4. Realtime WebSocket Protocol

### Connection

```
wss://<PROJECT_REF>.supabase.co/realtime/v1/websocket?apikey=<ANON_KEY>&vsn=1.0.0
```

Query parameters:
- `apikey`: Project anon key (required)
- `vsn`: Protocol version - `1.0.0` (JSON) or `2.0.0` (array/binary)
- `log_level`: Server-side logging level (optional)

### Message Format

#### Version 1.0.0 (JSON text frames)
```json
{
  "event": "phx_join",
  "topic": "realtime:channel_name",
  "payload": { ... },
  "ref": "unique_ref_id",
  "join_ref": "join_reference"
}
```

#### Version 2.0.0 (array text frames)
```json
["join_ref", "ref", "topic", "event", { payload }]
```

Binary frames (v2): type byte prefix
- `0x03`: User broadcast push
- `0x04`: User broadcast

### Topic Format
```
realtime:<channel_name>
```

### Core Events

| Event         | Direction      | Purpose                           |
|---------------|----------------|-----------------------------------|
| `phx_join`    | Client -> Server | Subscribe to channel             |
| `phx_leave`   | Client -> Server | Unsubscribe from channel         |
| `phx_reply`   | Server -> Client | Response to client message       |
| `phx_error`   | Server -> Client | Error notification               |
| `heartbeat`   | Client -> Server | Keep-alive (topic: `phoenix`)    |
| `access_token`| Client -> Server | Refresh authentication           |
| `postgres_changes` | Server -> Client | DB change notification      |
| `broadcast`   | Both           | Custom broadcast messages         |
| `presence_state` | Server -> Client | Full presence state           |
| `presence_diff`  | Server -> Client | Presence changes              |

### Heartbeat

Send every **25 seconds** to prevent timeout:
```json
{
  "event": "heartbeat",
  "topic": "phoenix",
  "payload": {},
  "ref": "heartbeat_ref"
}
```

### Channel Join (phx_join)

```json
{
  "event": "phx_join",
  "topic": "realtime:my_channel",
  "payload": {
    "config": {
      "broadcast": { "self": false, "ack": false },
      "presence": { "key": "optional-custom-key" },
      "postgres_changes": [
        {
          "event": "INSERT",
          "schema": "public",
          "table": "messages",
          "filter": "room_id=eq.1"
        }
      ]
    },
    "access_token": "<JWT>"
  },
  "ref": "1"
}
```

### Channel Leave
```json
{
  "event": "phx_leave",
  "topic": "realtime:my_channel",
  "payload": {},
  "ref": "2"
}
```

### Authentication Refresh
```json
{
  "event": "access_token",
  "topic": "realtime:my_channel",
  "payload": { "access_token": "<new_jwt>" },
  "ref": "3"
}
```

### Postgres Changes

#### Subscribe via phx_join config
```json
"postgres_changes": [
  {
    "event": "INSERT",     // INSERT, UPDATE, DELETE, or *
    "schema": "public",
    "table": "todos",      // optional (omit for all tables in schema)
    "filter": "id=eq.1"   // optional row-level filter
  }
]
```

#### Filter operators (same as PostgREST)
`eq`, `neq`, `lt`, `lte`, `gt`, `gte`, `in`

**Limitation:** DELETE events cannot be filtered.

#### Change event payload
```json
{
  "event": "postgres_changes",
  "topic": "realtime:my_channel",
  "payload": {
    "data": {
      "type": "INSERT",
      "schema": "public",
      "table": "todos",
      "commit_timestamp": "2024-01-01T00:00:00Z",
      "new": { "id": 1, "title": "New todo" },
      "old": {}
    }
  }
}
```

- `new`: Populated for INSERT and UPDATE
- `old`: Populated for UPDATE and DELETE (requires `REPLICA IDENTITY FULL` on the table)
- DELETE with RLS + replica identity full: `old` contains only primary keys

#### Setup requirements
1. Add table to publication: `ALTER PUBLICATION supabase_realtime ADD TABLE <table>`
2. For old records: `ALTER TABLE <table> REPLICA IDENTITY FULL`

### Broadcast

#### Send via WebSocket
```json
{
  "event": "broadcast",
  "topic": "realtime:my_channel",
  "payload": {
    "event": "cursor_move",
    "payload": { "x": 100, "y": 200 }
  },
  "ref": "4"
}
```

#### Send via REST API
```
POST https://<PROJECT_REF>.supabase.co/realtime/v1/api/broadcast
Headers:
  apikey: <KEY>
  Content-Type: application/json
Body: {
  "messages": [
    {
      "topic": "channel_name",
      "event": "event_type",
      "payload": { "key": "value" }
    }
  ]
}
```

#### Broadcast config options
- `self: true` - Receive own broadcasts (default: false)
- `ack: true` - Get server acknowledgment (default: false)

#### Broadcast replay (private channels only)
- `since`: Unix timestamp in milliseconds
- `limit`: Max 25 messages
- Replayed messages include `meta.replayed` flag
- Messages retained 3 days

### Presence

#### Track state
Send after joining:
```json
{
  "event": "presence",
  "topic": "realtime:my_channel",
  "payload": {
    "type": "track",
    "payload": { "userId": 1, "typing": false }
  },
  "ref": "5"
}
```

#### Untrack
```json
{
  "event": "presence",
  "topic": "realtime:my_channel",
  "payload": { "type": "untrack" },
  "ref": "6"
}
```

#### Presence events from server

**presence_state** (full state on join):
```json
{
  "client_key_1": [{ "userId": 1, "typing": false }],
  "client_key_2": [{ "userId": 2, "typing": true }]
}
```

**presence_diff** (incremental updates):
```json
{
  "joins": { "key": [{ presence payload }] },
  "leaves": { "key": [{ presence payload }] }
}
```

Three client-side events: `sync`, `join`, `leave`

#### Presence key
- Default: server-generated UUIDv1
- Custom: set in channel config `presence: { key: "userId-123" }`
- Must be unique per client in channel

### Performance Note

Database authorization for Postgres Changes runs on a single thread. For N subscribed clients and 1 change event, N authorization checks execute sequentially. High-throughput scenarios should consider public tables without RLS.

---

## 5. Storage API

Base path: `/storage/v1`

### Bucket Operations

| Method | Path                         | Body / Params                                          |
|--------|------------------------------|-------------------------------------------------------|
| POST   | `/storage/v1/bucket/`        | `{ "name": "avatars", "public": false, "file_size_limit": 5242880, "allowed_mime_types": ["image/*"] }` |
| GET    | `/storage/v1/bucket/`        | Query: `limit`, `offset`, `sortColumn`, `sortOrder`, `search` |
| GET    | `/storage/v1/bucket/{id}`    | -                                                      |
| PUT    | `/storage/v1/bucket/{id}`    | `{ "public": true, "file_size_limit": 10485760 }`     |
| POST   | `/storage/v1/bucket/{id}/empty` | - (empties all objects)                              |
| DELETE | `/storage/v1/bucket/{id}`    | -                                                      |

### Object Operations

#### Upload (Standard - up to 6MB recommended, 5GB max)
```
POST /storage/v1/object/<bucket>/<path/to/file>
Headers:
  apikey: <KEY>
  Authorization: Bearer <TOKEN>
  Content-Type: <mime_type>      (auto-detected from extension if omitted)
  x-upsert: true                 (optional, overwrite existing)
Response: { "Id": "...", "Key": "bucket/path/file.png" }
```

#### Upload (Resumable / TUS - for files > 6MB)

**Endpoint:** `https://<PROJECT_REF>.storage.supabase.co/storage/v1/upload/resumable`

**Step 1: Create upload**
```
POST /storage/v1/upload/resumable
Headers:
  authorization: Bearer <TOKEN>
  tus-resumable: 1.0.0
  upload-length: <file_size_in_bytes>
  upload-metadata: bucketName <base64>,objectName <base64>,contentType <base64>,cacheControl <base64>
  x-upsert: true                 (optional)
Response: Location header with unique upload URL
```

**Step 2: Upload chunks**
```
PATCH <upload_url>
Headers:
  tus-resumable: 1.0.0
  upload-offset: <byte_offset>
  Content-Type: application/offset+octet-stream
Body: <chunk_data>
```

**Step 3: Check progress**
```
HEAD <upload_url>
Headers:
  tus-resumable: 1.0.0
Response: upload-offset header with current position
```

**TUS constraints:**
- Chunk size: **6MB fixed** (do not change)
- Upload URL valid for 24 hours
- Single client per upload URL (concurrent = 409 Conflict)

#### Download / Serve

**Public bucket:**
```
GET /storage/v1/object/public/<bucket>/<path>
GET /storage/v1/object/public/<bucket>/<path>?download
GET /storage/v1/object/public/<bucket>/<path>?download=custom-name.jpg
```

**Authenticated (private bucket):**
```
GET /storage/v1/object/authenticated/<bucket>/<path>
Headers:
  Authorization: Bearer <TOKEN>
```

**Signed URL (time-limited access):**
```
POST /storage/v1/object/sign/<bucket>/<path>
Headers:
  apikey: <KEY>
  Authorization: Bearer <TOKEN>
  Content-Type: application/json
Body: { "expiresIn": 3600 }
Response: { "signedURL": "/storage/v1/object/sign/bucket/path?token=..." }
```

**Retrieve via signed URL:**
```
GET /storage/v1/object/sign/<bucket>/<path>?token=<token>&download
```

**Batch signed URLs:**
```
POST /storage/v1/object/sign/<bucket>
Body: { "expiresIn": 3600, "paths": ["file1.jpg", "file2.jpg"] }
Response: [{ "signedURL": "...", "path": "..." }]
```

#### Object Info
```
GET /storage/v1/object/info/<bucket>/<path>
GET /storage/v1/object/info/public/<bucket>/<path>
```

#### Update Object
```
PUT /storage/v1/object/<bucket>/<path>
Headers: (same as upload)
```

#### Delete Object
```
DELETE /storage/v1/object/<bucket>/<path>
```

#### Delete Multiple
```
DELETE /storage/v1/object/<bucket>
Body: { "prefixes": ["path1.jpg", "path2.jpg"] }
```

#### Move Object
```
POST /storage/v1/object/move
Body: {
  "bucketId": "source_bucket",
  "sourceKey": "old/path.jpg",
  "destinationKey": "new/path.jpg",
  "destinationBucket": "target_bucket"    // optional
}
```

#### Copy Object
```
POST /storage/v1/object/copy
Body: {
  "bucketId": "source_bucket",
  "sourceKey": "path.jpg",
  "destinationKey": "copy.jpg",
  "destinationBucket": "target_bucket",   // optional
  "copyMetadata": true                     // optional
}
```

#### List / Search Objects
```
POST /storage/v1/object/list/<bucket>
Body: {
  "prefix": "folder/",
  "limit": 100,
  "offset": 0,
  "sortBy": { "column": "name", "order": "asc" },
  "search": "query"
}
```

### Concurrency
- Without `x-upsert`: first upload wins, subsequent get `400 Asset Already Exists`
- With `x-upsert`: last upload wins

---

## 6. Edge Functions

### Invoke
```
POST https://<PROJECT_REF>.supabase.co/functions/v1/<function-name>
Headers:
  apikey: <KEY>
  Authorization: Bearer <TOKEN>
  Content-Type: application/json
Body: { "param1": "value1" }
Response: application/json
```

- Local dev: `http://localhost:54321/functions/v1/<function-name>`
- JWT verification can be disabled with `--no-verify-jwt` flag on deploy (for webhooks)
- CORS support available for browser invocations

---

## 7. Error Responses & Rate Limiting

### PostgREST Error Format
```json
{
  "message": "description of the error",
  "code": "PGRST116",
  "details": "additional context",
  "hint": "suggested fix"
}
```

### Auth Error Format
```json
{
  "error": "error_type",
  "error_description": "Human readable message"
}
```

### Storage Error Responses
- `400 Asset Already Exists` - Upload to existing path without upsert
- `409 Conflict` - Concurrent TUS upload to same URL

### HTTP Status Codes (PostgREST)
- `200` - Success (GET, PATCH, DELETE with return=representation)
- `201` - Created (POST with return=representation)
- `204` - No Content (with return=minimal)
- `400` - Bad request / malformed query
- `401` - Unauthorized (missing/invalid JWT)
- `403` - Forbidden (RLS policy violation)
- `404` - Not found (table/function doesn't exist)
- `406` - Not acceptable
- `409` - Conflict (unique constraint violation)
- `416` - Range not satisfiable

### Rate Limiting

Supabase applies rate limiting at the API gateway level. Specific limits depend on the project's
pricing tier. The documentation does not publish exact rate limit numbers but notes that the API
gateway enforces key-auth and rate controls.

### Connection Limits

| Connection Method        | Port  | Mode         | Notes                              |
|--------------------------|-------|--------------|-------------------------------------|
| Direct Postgres          | 5432  | -            | IPv6 primary, IPv4 paid add-on     |
| Session pooler           | 5432  | Session      | Via PgBouncer proxy                |
| Transaction pooler       | 6543  | Transaction  | No prepared statements             |
| Dedicated pooler         | 6543  | Transaction  | Co-located, paid tier              |

Connection string format:
```
postgresql://postgres.[PROJECT]:[PASSWORD]@aws-0-[REGION].pooler.supabase.com:6543/postgres
```

SSL recommended for all connections. Server root certificate available from dashboard.

---

## Appendix: Type Generation

Generate TypeScript types from database schema:

```bash
# From remote project
npx supabase gen types typescript --project-id "$PROJECT_REF" --schema public > database.types.ts

# From local dev
npx supabase gen types typescript --local > database.types.ts

# From connection string
npx supabase gen types typescript --db-url postgres://... --schema public > database.types.ts
```

Types map to three variants per table:
- **Row** - SELECT result shape
- **Insert** - INSERT input shape
- **Update** - PATCH input shape (all fields optional)
