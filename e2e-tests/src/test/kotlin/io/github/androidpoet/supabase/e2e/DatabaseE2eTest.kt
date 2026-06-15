package io.github.androidpoet.supabase.e2e

import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.database.createDatabaseClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseE2eTest {
    @Test
    fun test_database_insertThenSelect_returnsInsertedRow() =
        runTest {
            val config = localSupabaseConfig()
            val client =
                Supabase.create(
                    projectUrl = config.apiUrl,
                    apiKey = config.anonKey,
                )
            val database = createDatabaseClient(client)
            val messageBody = "e2e-${UUID.randomUUID()}"
            val payload =
                buildJsonObject {
                    put("body", messageBody)
                }.toString()

            val inserted =
                database
                    .insert(
                        table = E2E_MESSAGES_TABLE,
                        body = payload,
                    ).getOrThrow()
            val selected =
                database
                    .select(
                        table = E2E_MESSAGES_TABLE,
                    ) {
                        eq("body", messageBody)
                    }.getOrThrow()

            client.close()
            assertTrue(inserted.contains(messageBody), inserted)
            val rows = Json.parseToJsonElement(selected).jsonArray
            assertEquals(1, rows.size, selected)
            assertEquals(
                messageBody,
                rows
                    .single()
                    .jsonObject["body"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }
}

private const val E2E_MESSAGES_TABLE = "e2e_messages"

private data class LocalSupabaseConfig(
    val apiUrl: String,
    val anonKey: String,
)

private fun localSupabaseConfig(): LocalSupabaseConfig {
    val envUrl = System.getenv("SUPABASE_E2E_URL")
    val envAnonKey = System.getenv("SUPABASE_E2E_ANON_KEY")
    if (!envUrl.isNullOrBlank() && !envAnonKey.isNullOrBlank()) {
        return LocalSupabaseConfig(apiUrl = envUrl, anonKey = envAnonKey)
    }

    val status =
        ProcessBuilder("supabase", "status", "-o", "env")
            .directory(File(System.getProperty("user.dir")))
            .redirectErrorStream(true)
            .start()
    val output = status.inputStream.bufferedReader().readText()
    val exitCode = status.waitFor()
    check(exitCode == 0) {
        "Unable to read local Supabase status. Run `supabase start` first.\n$output"
    }
    val values =
        output
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1].trim('"') else null
            }.toMap()
    val apiUrl =
        values["API_URL"]
            ?: values["SUPABASE_URL"]
            ?: error("Supabase status did not include API_URL.\n$output")
    val anonKey =
        values["ANON_KEY"]
            ?: values["SUPABASE_ANON_KEY"]
            ?: error("Supabase status did not include ANON_KEY.\n$output")
    return LocalSupabaseConfig(apiUrl = apiUrl, anonKey = anonKey)
}
