package io.github.androidpoet.supabase.client.transport

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun platformEngine(): HttpClientEngineFactory<*> = CIO
