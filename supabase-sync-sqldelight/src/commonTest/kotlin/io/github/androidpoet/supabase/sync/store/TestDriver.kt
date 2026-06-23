package io.github.androidpoet.supabase.sync.store

import app.cash.sqldelight.db.SqlDriver

/** An in-memory SQLite driver with the schema already created, for tests. */
internal expect fun testDriver(): SqlDriver
