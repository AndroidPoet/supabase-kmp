package io.github.androidpoet.supabase.client.transport

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData

internal class TestMockEngineFactory(
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) : HttpClientEngineFactory<MockEngineConfig> {
    override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
        val config =
            MockEngineConfig().apply {
                addHandler(handler)
                block()
            }
        return MockEngine(config)
    }
}
