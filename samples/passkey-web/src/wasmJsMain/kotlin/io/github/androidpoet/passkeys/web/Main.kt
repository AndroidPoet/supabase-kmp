package io.github.androidpoet.passkeys.web

import io.github.androidpoet.passkeys.WasmJsPasskeyClient
import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.createAuthClient
import io.github.androidpoet.supabase.auth.passkey.PasskeysKmpAuthenticator
import io.github.androidpoet.supabase.auth.passkey.registerPasskey
import io.github.androidpoet.supabase.auth.passkey.signInWithPasskey
import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import kotlin.random.Random

/** Reads a value off the JS global scope (set by config.js) — keeps secrets out of the build. */
private fun jsGlobal(key: String): String = js("globalThis[key] || ''")

/**
 * Browser passkey sample. The whole ceremony runs through supabase-kmp's own
 * helpers; the only web-specific line is constructing [WasmJsPasskeyClient],
 * which drives `navigator.credentials` natively in the browser.
 */
fun main() {
    val log = document.getElementById("log") as HTMLElement

    fun append(line: String) {
        log.textContent = (log.textContent ?: "") + "\n" + line
    }

    val url = jsGlobal("SUPABASE_URL")
    val anonKey = jsGlobal("SUPABASE_ANON_KEY")
    if (url.isEmpty() || anonKey.isEmpty()) {
        append("Missing config — set SUPABASE_URL / SUPABASE_ANON_KEY in config.js")
        return
    }

    val supabase = Supabase.create(projectUrl = url, apiKey = anonKey)
    val auth: AuthClient = createAuthClient(supabase)
    val authenticator = PasskeysKmpAuthenticator(WasmJsPasskeyClient())
    val scope = MainScope()

    document.getElementById("register")?.addEventListener("click", {
        scope.launch {
            val email = "pk-${Random.nextLong().toString(16)}@example.com"
            val password = "Pk!${Random.nextLong().toString(16)}A1"
            append("\nSigning up $email …")
            when (val signUp = auth.signUpWithEmail(email = email, password = password)) {
                is SupabaseResult.Success -> {
                    append("User ${signUp.value.user.id}. Registration ceremony…")
                    when (val reg = auth.registerPasskey(signUp.value.accessToken, authenticator)) {
                        is SupabaseResult.Success -> append("Registered passkey id=${reg.value.id}")
                        is SupabaseResult.Failure -> append("Register failed: ${reg.error.code} ${reg.error.message}")
                    }
                }
                is SupabaseResult.Failure -> append("Sign-up failed: ${signUp.error.message}")
            }
        }
    })

    document.getElementById("signin")?.addEventListener("click", {
        scope.launch {
            append("\nAuthentication ceremony…")
            when (val res = auth.signInWithPasskey(authenticator)) {
                is SupabaseResult.Success -> append("Signed in. user=${res.value.user.id}")
                is SupabaseResult.Failure -> append("Sign-in failed: ${res.error.code} ${res.error.message}")
            }
        }
    })

    append("Ready.")
}
