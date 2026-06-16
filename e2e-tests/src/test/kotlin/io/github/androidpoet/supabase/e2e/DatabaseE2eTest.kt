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
                    ).unwrap("insert")
            val selected =
                database
                    .select(
                        table = E2E_MESSAGES_TABLE,
                    ) {
                        eq("body", messageBody)
                    }.unwrap("select")

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
