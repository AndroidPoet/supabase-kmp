package io.github.androidpoet.supabase.sync.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import io.github.androidpoet.supabase.sync.store.db.OfflineSyncDb

internal actual fun testDriver(): SqlDriver = inMemoryDriver(OfflineSyncDb.Schema)
