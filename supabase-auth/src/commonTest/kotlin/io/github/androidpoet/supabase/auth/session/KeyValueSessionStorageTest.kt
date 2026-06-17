package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyValueSessionStorageTest {
    @Test
    fun test_load_roundTripsUsableSession() =
        runTest {
            val store = FakeKeyValueStore()
            val storage = KeyValueSessionStorage(store)

            storage.save(validSession)

            assertEquals(validSession, storage.load())
        }

    @Test
    fun test_load_returnsNullForCorruptPayload() =
        runTest {
            val store = FakeKeyValueStore()
            store.set(KeyValueSessionStorage.DEFAULT_KEY, "not-json{")

            assertNull(KeyValueSessionStorage(store).load())
        }

    @Test
    fun test_load_rejectsStructurallyUnusableSession() =
        runTest {
            // Valid JSON, but the refresh token is empty — the session can never be
            // renewed, so restoring it would only yield a 401 later.
            val store = FakeKeyValueStore()
            store.set(
                KeyValueSessionStorage.DEFAULT_KEY,
                """{"access_token":"acc","refresh_token":"","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}""",
            )

            assertNull(KeyValueSessionStorage(store).load())
        }

    @Test
    fun test_load_rejectsNegativeExpiry() =
        runTest {
            val store = FakeKeyValueStore()
            store.set(
                KeyValueSessionStorage.DEFAULT_KEY,
                """{"access_token":"acc","refresh_token":"ref","expires_in":-1,"token_type":"bearer","user":{"id":"u1"}}""",
            )

            assertNull(KeyValueSessionStorage(store).load())
        }
}

private val validSession =
    Session(
        accessToken = "acc",
        refreshToken = "ref",
        expiresIn = 3600,
        tokenType = "bearer",
        user = User(id = "u1"),
    )

private class FakeKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = map[key]

    override suspend fun set(key: String, value: String) {
        map[key] = value
    }

    override suspend fun remove(key: String) {
        map.remove(key)
    }
}
