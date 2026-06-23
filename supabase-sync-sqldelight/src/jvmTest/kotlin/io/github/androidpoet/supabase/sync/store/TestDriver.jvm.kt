package io.github.androidpoet.supabase.sync.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.androidpoet.supabase.sync.store.db.OfflineSyncDb

internal actual fun testDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { OfflineSyncDb.Schema.create(it) }
