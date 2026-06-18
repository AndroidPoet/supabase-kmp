package io.github.androidpoet.supabase.auth.passkey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasskeyOptionsTest {
    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun test_normalize_stripsBase64UrlPaddingFromChallenge() {
        val result = normalizePasskeyCeremonyOptions(parse("""{"challenge":"YWJj==","rp":{"id":"example.com"}}"""))

        assertEquals("YWJj", result["challenge"]?.jsonPrimitive?.content)
        // Unrelated fields are preserved.
        assertEquals(
            "example.com",
            result["rp"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun test_normalize_stripsPaddingAndDefaultsTransportsOnCredentialDescriptors() {
        val result =
            normalizePasskeyCeremonyOptions(
                parse(
                    """{"challenge":"Yw","allowCredentials":[{"type":"public-key","id":"Zm9vYmFy=="}]}""",
                ),
            )

        val cred = result["allowCredentials"]?.jsonArray?.single()?.jsonObject
        assertEquals("Zm9vYmFy", cred?.get("id")?.jsonPrimitive?.content)
        // A transports array is added when the server omitted it.
        assertTrue(cred?.get("transports") is JsonArray)
        assertEquals(0, cred?.get("transports")?.jsonArray?.size)
    }

    @Test
    fun test_normalize_unwrapsPublicKeyEnvelope() {
        val result = normalizePasskeyCeremonyOptions(parse("""{"publicKey":{"challenge":"YQ==","timeout":60000}}"""))

        // The inner publicKey object becomes the root, with its challenge cleaned.
        assertEquals("YQ", result["challenge"]?.jsonPrimitive?.content)
        assertEquals(60000, result["timeout"]?.jsonPrimitive?.content?.toInt())
        assertTrue(result["publicKey"] == null)
    }

    @Test
    fun test_normalize_preservesExistingTransports() {
        val result =
            normalizePasskeyCeremonyOptions(
                parse(
                    """{"challenge":"a","excludeCredentials":[{"id":"b","transports":["internal","hybrid"]}]}""",
                ),
            )

        val transports =
            result["excludeCredentials"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("transports")
                ?.jsonArray
        assertEquals(2, transports?.size)
        assertEquals("internal", transports?.first()?.jsonPrimitive?.content)
    }

    @Test
    fun test_normalize_leavesUnpaddedValuesUnchanged() {
        val input = parse("""{"challenge":"abc","user":{"id":"dXNlcg","name":"a@b.com"}}""")

        val result = normalizePasskeyCeremonyOptions(input)

        assertEquals("abc", result["challenge"]?.jsonPrimitive?.content)
        // user.id is not a credential-descriptor id, so it is passed through verbatim.
        assertEquals(
            "dXNlcg",
            result["user"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(JsonPrimitive("a@b.com"), result["user"]?.jsonObject?.get("name"))
    }
}
