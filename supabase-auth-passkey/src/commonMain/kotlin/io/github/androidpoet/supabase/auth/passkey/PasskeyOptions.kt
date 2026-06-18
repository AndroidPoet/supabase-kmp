package io.github.androidpoet.supabase.auth.passkey

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Normalizes the WebAuthn options object Supabase returns into the canonical W3C
 * JSON form an authenticator expects, mirroring `supabase_flutter`'s options
 * mapper:
 *
 *  - unwraps a `publicKey` envelope when the options are nested under one;
 *  - strips base64url `=` padding from `challenge` and from every
 *    `allowCredentials[].id` / `excludeCredentials[].id` — the W3C JSON form is
 *    unpadded and some authenticators reject padded values;
 *  - defaults each credential descriptor's `transports` to an empty array.
 *
 * [registerPasskey] and [signInWithPasskey] apply this before the ceremony, so
 * every [PasskeyAuthenticator] — the bundled Android one and any you implement
 * for other platforms — receives already-clean options.
 */
internal fun normalizePasskeyCeremonyOptions(options: JsonObject): JsonObject {
    val root = (options["publicKey"] as? JsonObject) ?: options
    return buildJsonObject {
        for ((key, value) in root) {
            when (key) {
                "challenge" -> put("challenge", value.stripBase64UrlPadding())
                "allowCredentials", "excludeCredentials" ->
                    put(key, (value as? JsonArray).normalizeCredentialDescriptors())
                else -> put(key, value)
            }
        }
    }
}

private fun JsonElement.stripBase64UrlPadding(): JsonElement =
    (this as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.let { JsonPrimitive(it.content.trimEnd('=')) }
        ?: this

private fun JsonArray?.normalizeCredentialDescriptors(): JsonArray =
    buildJsonArray {
        this@normalizeCredentialDescriptors?.forEach { element ->
            val descriptor = element as? JsonObject
            if (descriptor == null) {
                add(element)
                return@forEach
            }
            add(
                buildJsonObject {
                    for ((key, value) in descriptor) {
                        if (key == "id") put("id", value.stripBase64UrlPadding()) else put(key, value)
                    }
                    // The W3C credential descriptor requires a transports array; some
                    // authenticators reject its absence.
                    if ("transports" !in descriptor) put("transports", buildJsonArray {})
                },
            )
        }
    }
