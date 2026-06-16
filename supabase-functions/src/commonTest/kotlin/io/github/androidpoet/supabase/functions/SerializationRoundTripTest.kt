package io.github.androidpoet.supabase.functions

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_functionRegion_decodesFromSerialName() {
        assertEquals(
            FunctionRegion.US_EAST_1,
            json.decodeFromString<FunctionRegion>("\"us-east-1\""),
        )
        assertEquals(
            FunctionRegion.AP_NORTHEAST_1,
            json.decodeFromString<FunctionRegion>("\"ap-northeast-1\""),
        )
    }

    @Test
    fun test_functionRegion_roundTripAllValues() {
        for (region in FunctionRegion.entries) {
            val decoded = json.decodeFromString<FunctionRegion>(json.encodeToString(region))
            assertEquals(region, decoded)
        }
    }

    @Test
    fun test_functionRegion_encodesToWireName() {
        assertEquals("\"eu-west-1\"", json.encodeToString(FunctionRegion.EU_WEST_1))
    }
}
