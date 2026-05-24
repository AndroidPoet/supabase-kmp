package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RealtimeChannelBuilderConfigTest {
    @Test
    fun test_builder_configValuesPropagateToSubscription() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))

        val subscription = realtime
            .channel("room-1")
            .configureBroadcast(receiveOwnBroadcasts = true, acknowledgeBroadcasts = true)
            .configureBroadcastReplay(sinceMs = 1234L, limit = 10)
            .configurePresence(key = "user-123")
            .setPrivate(true)
            .subscribe()

        val internal = subscription as ChannelSubscriptionImpl
        assertEquals(true, internal.receiveOwnBroadcasts)
        assertEquals(true, internal.acknowledgeBroadcasts)
        assertEquals("user-123", internal.presenceKey)
        assertEquals(true, internal.privateChannel)
        assertEquals(1234L, internal.replaySinceMs)
        assertEquals(10, internal.replayLimit)
    }

    @Test
    fun test_removeSubscription_unsubscribesChannel() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val subscription = realtime.channel("room-2").subscribe()

        realtime.removeSubscription(subscription)

        assertTrue(subscription.status.value == RealtimeSubscription.Status.UNSUBSCRIBED)
    }

    @Test
    fun test_removeChannel_subscription_unsubscribesChannel() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val subscription = realtime.channel("room-2b").subscribe()

        realtime.removeChannel(subscription)

        assertTrue(subscription.status.value == RealtimeSubscription.Status.UNSUBSCRIBED)
    }

    @Test
    fun test_removeSubscriptions_unsubscribesAllChannels() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val first = realtime.channel("room-2c-1").subscribe()
        val second = realtime.channel("room-2c-2").subscribe()

        realtime.removeSubscriptions(listOf(first, second))

        assertTrue(first.status.value == RealtimeSubscription.Status.UNSUBSCRIBED)
        assertTrue(second.status.value == RealtimeSubscription.Status.UNSUBSCRIBED)
    }

    @Test
    fun test_activeChannelDetails_returnsNameAndTopic() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        realtime.channel("room-3").subscribe()

        assertEquals(
            setOf(RealtimeChannel(name = "room-3", topic = "realtime:room-3")),
            realtime.activeChannelDetails(),
        )
    }

    @Test
    fun test_getSubscription_returnsSubscriptionForChannel() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val subscription = realtime.channel("room-4").subscribe()

        assertTrue(realtime.getSubscription("room-4") === subscription)
    }

    @Test
    fun test_getSubscriptionByTopic_returnsSubscription() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val subscription = realtime.channel("room-5").subscribe()

        assertTrue(realtime.getSubscriptionByTopic("realtime:room-5") === subscription)
    }

    @Test
    fun test_removeSubscriptionByTopic_unsubscribesChannel() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val subscription = realtime.channel("room-topic").subscribe()

        realtime.removeSubscriptionByTopic("realtime:room-topic")

        assertTrue(subscription.status.value == RealtimeSubscription.Status.UNSUBSCRIBED)
    }

    @Test
    fun test_removeChannelsByTopic_unsubscribesAllMatchingChannels() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val first = realtime.channel("room-t1").subscribe()
        val second = realtime.channel("room-t2").subscribe()

        realtime.removeChannelsByTopic(listOf("realtime:room-t1", "realtime:room-t2"))

        assertTrue(first.status.value == RealtimeSubscription.Status.UNSUBSCRIBED)
        assertTrue(second.status.value == RealtimeSubscription.Status.UNSUBSCRIBED)
    }

    @Test
    fun test_getSubscriptions_returnsAllActiveSubscriptions() = runBlocking {
        val client = FakeSupabaseClient()
        val realtime = RealtimeClientImpl(client, RealtimeConfig(autoReconnect = false))
        val first = realtime.channel("room-a").subscribe()
        val second = realtime.channel("room-b").subscribe()

        val subscriptions = realtime.getSubscriptions()
        assertEquals(2, subscriptions.size)
        assertTrue(subscriptions.contains(first))
        assertTrue(subscriptions.contains(second))
    }
}

private class FakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))
    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun delete(
        endpoint: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override fun close() = Unit
}
