# Passkey web sample (Kotlin/Wasm)

A minimal browser demo of Supabase passkeys driven entirely by supabase-kmp's
own helpers. The whole WebAuthn ceremony runs through
[`PasskeysKmpAuthenticator`](../../supabase-auth-passkey), which wraps the
cross-platform [`passkeys-kmp`](https://github.com/AndroidPoet/passkeys-kmp)
`WasmJsPasskeyClient` — so the browser's native `navigator.credentials` does the
real work.

## Setup

1. Copy the config template and fill in your project's values:

   ```bash
   cp src/wasmJsMain/resources/config.example.js src/wasmJsMain/resources/config.js
   ```

   `config.js` is gitignored — your URL and anon key never get committed.

2. In your Supabase project's **Authentication → Passkeys** settings, configure
   the relying party for local development:

   - **Relying Party ID:** `localhost`
   - **Relying Party Origins:** `http://localhost:8080`

   (WebAuthn treats `localhost` as a secure context, so no domain or
   `assetlinks.json` is needed for the web flow.)

## Run

```bash
./gradlew :samples:passkey-web:wasmJsBrowserDevelopmentRun
```

Open the served URL, then **Sign up + register passkey** and **Sign in with
passkey**. Sign-in uses a discoverable credential, so no email is typed — the
browser resolves the account from the passkey itself.
