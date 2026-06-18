package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

private const val MAX_OUTBOUND_BUFFER = 100

internal class RealtimeClientImpl(
    private val supabaseClient: SupabaseClient,
    private val config: RealtimeConfig = RealtimeConfig(),
    engineFactory: HttpClientEngineFactory<*> = platformEngine(),
) : RealtimeClient {
    private val json =
        Json {
            // Match the rest of the SDK: tolerate fields newer servers add, be lenient on
            // wire quirks, and never serialize explicit nulls into outbound Phoenix frames.
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    private val httpClient =
        HttpClient(engineFactory) {
            install(WebSockets)
        }

    @Volatile
    private var session: WebSocketSession? = null

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var reconnectJob: Job? = null

    // One long-lived scope for the reconnect timer, created once instead of per
    // attempt — a fresh SupervisorJob scope on every scheduleReconnect() was never
    // cancelled and leaked. Its only child is reconnectJob (cancelled on disconnect);
    // the scope itself is cancelled in close().
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Phoenix refs must be unique; nextRef() can run from concurrent suspend
    // calls, so use an atomic counter (kotlinx-atomicfu — the stdlib
    // kotlin.concurrent.atomics is only available from Kotlin 2.1.20).
    private val refCounter = atomic(0)

    // Reset from connect()/disconnect() (caller coroutine) AND incremented in the reconnect loop,
    // so a plain @Volatile var would race on the ++ (a read-modify-write). Use an atomic counter,
    // like refCounter above.
    private val reconnectAttempt = atomic(0)

    @Volatile
    private var authTokenOverride: String? = null

    @Volatile
    private var intentionalDisconnect = false

    // Set when a heartbeat is sent, cleared when its reply arrives. If it is still
    // set at the next heartbeat tick, the server went silent: tear the socket down
    // so the normal reconnect path runs (otherwise the connection is a zombie).
    // The heartbeat tick (one coroutine) and the inbound reply handler (another)
    // race on this, so it is atomic: the tick takes the value with a single
    // getAndSet, which can't observe a half-applied reply and false-positive.
    private val pendingHeartbeatRef = atomic<String?>(null)

    // Outbound messages issued while the socket is down (e.g. a broadcast sent
    // mid-reconnect) would otherwise be silently dropped by `session?.send`.
    // Buffer non-heartbeat application messages and replay them once rejoined.
    private val outboundBuffer = mutableListOf<RealtimeMessage>()
    private val outboundBufferLock = Mutex()

    // Serializes connect() so concurrent callers can't each open a socket. The
    // reconnect loop calls establishConnection() directly (not connect()), so it
    // never contends on this; only the public connect() entry point takes it.
    private val connectMutex = Mutex()
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _debugState = MutableStateFlow(RealtimeDebugState())
    override val debugState: StateFlow<RealtimeDebugState> = _debugState.asStateFlow()
    private val _debugEvents = MutableSharedFlow<RealtimeDebugEvent>(extraBufferCapacity = 64)
    override val debugEvents: Flow<RealtimeDebugEvent> = _debugEvents
    override val isConnected: Boolean get() = _connectionState.value is ConnectionState.Connected
    override val isConnecting: Boolean get() = _connectionState.value is ConnectionState.Connecting
    override val isDisconnecting: Boolean get() = _connectionState.value is ConnectionState.Disconnecting

    // activeSubscriptions is read from non-suspend getters on arbitrary threads
    // and mutated from coroutines; guard every access with this lock. Callers must
    // snapshot under the lock and release it before invoking suspend functions.
    private val subscriptionsLock = SynchronizedObject()
    private val activeSubscriptions = mutableMapOf<String, ChannelSubscriptionImpl>()

    internal fun nextRef(): String = refCounter.incrementAndGet().toString()

    override fun channel(name: String): RealtimeChannelBuilder =
        RealtimeChannelBuilder(channelName = name, client = this)

    override fun getSubscription(name: String): RealtimeSubscription? =
        synchronized(subscriptionsLock) { activeSubscriptions["realtime:$name"] }

    override fun getSubscriptionByTopic(topic: String): RealtimeSubscription? =
        synchronized(subscriptionsLock) { activeSubscriptions[topic] }

    override fun getSubscriptions(): Set<RealtimeSubscription> =
        synchronized(subscriptionsLock) { activeSubscriptions.values.toSet() }

    override fun activeChannels(): Set<String> =
        synchronized(subscriptionsLock) { activeSubscriptions.values.mapTo(mutableSetOf()) { it.channel } }

    override fun activeChannelDetails(): Set<RealtimeChannel> =
        synchronized(subscriptionsLock) {
            activeSubscriptions.values.mapTo(mutableSetOf()) { RealtimeChannel(name = it.channel, topic = it.topic) }
        }

    override suspend fun removeSubscription(subscription: RealtimeSubscription) {
        subscription.unsubscribe()
    }

    override suspend fun removeSubscriptions(subscriptions: List<RealtimeSubscription>) {
        subscriptions.forEach { subscription ->
            subscription.unsubscribe()
        }
    }

    @Deprecated("Use removeSubscription instead", ReplaceWith("removeSubscription(subscription)"))
    override suspend fun removeChannel(subscription: RealtimeSubscription) {
        removeSubscription(subscription)
    }

    override suspend fun removeSubscriptionByTopic(topic: String) {
        synchronized(subscriptionsLock) { activeSubscriptions[topic] }?.unsubscribe()
    }

    override suspend fun removeChannelsByTopic(topics: List<String>) {
        topics.forEach { topic ->
            synchronized(subscriptionsLock) { activeSubscriptions[topic] }?.unsubscribe()
        }
    }

    override suspend fun removeChannel(name: String) {
        val topic = "realtime:$name"
        synchronized(subscriptionsLock) { activeSubscriptions[topic] }?.unsubscribe()
    }

    override suspend fun removeAllChannels() {
        synchronized(subscriptionsLock) { activeSubscriptions.values.toList() }
            .forEach { it.unsubscribe() }
    }

    override suspend fun setAuth(token: String?) {
        authTokenOverride = token
        val subscriptions = synchronized(subscriptionsLock) { activeSubscriptions.values.toList() }
        if (subscriptions.isEmpty()) return
        val accessToken = currentAccessToken()
        subscriptions.forEach { subscription ->
            sendMessage(
                RealtimeMessage(
                    topic = subscription.topic,
                    event = "access_token",
                    payload = buildJsonObject { put("access_token", JsonPrimitive(accessToken)) },
                    joinRef = subscription.joinRef,
                    ref = nextRef(),
                ),
            )
        }
    }

    override suspend fun sendHeartbeat() {
        sendHeartbeatMessage()
    }

    override suspend fun connect() {
        // Single-flight: two concurrent connect() calls both saw `session == null`
        // and each opened a socket, leaking one. Serialize through a mutex and
        // re-check inside the lock so the second caller no-ops on the first's
        // freshly-established session (or its in-progress Connecting state).
        connectMutex.withLock {
            if (session != null || _connectionState.value is ConnectionState.Connecting) return
            intentionalDisconnect = false
            _connectionState.value = ConnectionState.Connecting
            try {
                establishConnection()
                reconnectAttempt.value = 0
                _connectionState.value = ConnectionState.Connected
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                session = null
                if (config.autoReconnect && !intentionalDisconnect) {
                    scheduleReconnect()
                } else {
                    _connectionState.value =
                        ConnectionState.Failed(
                            reason = e.message ?: "Connection failed",
                            attempts = reconnectAttempt.value,
                        )
                }
            }
        }
    }

    override suspend fun broadcast(
        channel: String,
        event: String,
        payload: JsonObject,
        private: Boolean,
    ): SupabaseResult<Unit> {
        val body =
            buildJsonObject {
                put(
                    "messages",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("topic", JsonPrimitive(channel))
                                put("event", JsonPrimitive(event))
                                put("payload", payload)
                                put("private", JsonPrimitive(private))
                            },
                        ),
                    ),
                )
            }
        return supabaseClient
            .post(
                endpoint = "/realtime/v1/api/broadcast",
                body = json.encodeToString(body),
                headers = mapOf("Content-Type" to "application/json"),
            ).map { }
    }

    override suspend fun disconnect() {
        intentionalDisconnect = true
        _connectionState.value = ConnectionState.Disconnecting
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt.value = 0
        val toUnsubscribe =
            synchronized(subscriptionsLock) {
                val snapshot = activeSubscriptions.values.toList()
                activeSubscriptions.clear()
                snapshot
            }
        toUnsubscribe.forEach { it.unsubscribe() }
        heartbeatJob?.cancel()
        heartbeatJob = null
        session?.close()
        session = null
        scope?.cancel()
        scope = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun close() {
        disconnect()
        // The client is single-use after close(): tear down the reconnect scope and
        // release the Ktor engine (connection pool / background threads).
        reconnectScope.cancel()
        httpClient.close()
    }

    internal suspend fun subscribe(builder: RealtimeChannelBuilder): RealtimeSubscription {
        val topic = "realtime:${builder.channelName}"
        val subscription =
            ChannelSubscriptionImpl(
                channel = builder.channelName,
                topic = topic,
                client = this,
                postgresCallbacks = builder.postgresCallbacks.toList(),
                broadcastCallbacks = builder.broadcastCallbacks.toMap(),
                presenceCallback = builder.presenceCallback,
                receiveOwnBroadcasts = builder.receiveOwnBroadcasts,
                acknowledgeBroadcasts = builder.acknowledgeBroadcasts,
                presenceKey = builder.presenceKey,
                privateChannel = builder.privateChannel,
                replaySinceMs = builder.replaySinceMs,
                replayLimit = builder.replayLimit,
            )
        synchronized(subscriptionsLock) { activeSubscriptions[topic] = subscription }
        sendJoinMessage(subscription)
        return subscription
    }

    internal suspend fun subscribeWithResult(
        builder: RealtimeChannelBuilder,
    ): SupabaseResult<RealtimeSubscription> {
        val subscription = subscribe(builder)
        // Wait for the join to resolve instead of handing back a still-SUBSCRIBING
        // subscription. Bound it ourselves: if the socket is down the internal
        // join watchdog never launches, so the status would otherwise hang here.
        val terminal =
            withTimeoutOrNull(config.connectionTimeoutMs) {
                subscription.status.first { it != RealtimeSubscription.Status.SUBSCRIBING }
            }
        return if (terminal == RealtimeSubscription.Status.SUBSCRIBED) {
            SupabaseResult.Success(subscription)
        } else {
            // Timed out (null) or reached a non-subscribed terminal state (ERROR).
            // Ensure a timed-out join is marked failed for any status observers.
            (subscription as? ChannelSubscriptionImpl)?.markJoinTimedOut()
            SupabaseResult.Failure(
                SupabaseError(
                    message = "Channel '${builder.channelName}' failed to subscribe: ${terminal ?: "join timed out"}",
                ),
            )
        }
    }

    internal suspend fun leaveChannel(topic: String) {
        val subscription = synchronized(subscriptionsLock) { activeSubscriptions.remove(topic) }
        sendMessage(
            RealtimeMessage(
                topic = topic,
                event = "phx_leave",
                payload = buildJsonObject {},
                joinRef = subscription?.joinRef,
                ref = nextRef(),
            ),
        )
    }

    internal suspend fun sendMessage(message: RealtimeMessage) {
        recordOutboundMessage(message)
        val current = session
        if (current != null) {
            current.send(Frame.Text(json.encodeToString(message)))
            return
        }
        // Disconnected. Heartbeats and channel join/leave control frames are
        // regenerated on reconnect, so only buffer application messages.
        if (message.event in NON_BUFFERED_EVENTS) return
        outboundBufferLock.withLock {
            if (outboundBuffer.size >= MAX_OUTBOUND_BUFFER) {
                // The buffer is full: drop the oldest message to make room. Signal
                // the loss so fire-and-forget senders aren't silently truncated.
                val dropped = outboundBuffer.removeAt(0)
                _debugEvents.tryEmit(RealtimeDebugEvent.OutboundMessageDropped(dropped, MAX_OUTBOUND_BUFFER))
            }
            outboundBuffer.add(message)
        }
    }

    private suspend fun flushOutboundBuffer() {
        // Don't drain the buffer until we actually have a socket to send on —
        // clearing first and then bailing on a null session would silently lose
        // every buffered message.
        val current = session ?: return
        val pending =
            outboundBufferLock.withLock {
                val copy = outboundBuffer.toList()
                outboundBuffer.clear()
                copy
            }
        for ((index, message) in pending.withIndex()) {
            try {
                current.send(Frame.Text(json.encodeToString(message)))
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // The socket died mid-flush. Re-buffer everything we haven't sent yet
                // (this message included) at the front so it replays in order on the
                // next reconnect instead of being dropped.
                outboundBufferLock.withLock {
                    outboundBuffer.addAll(0, pending.subList(index, pending.size))
                }
                return
            }
        }
    }

    private suspend fun establishConnection() {
        val isHttps = supabaseClient.projectUrl.startsWith("https://")
        val host =
            supabaseClient.projectUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
        val wsScheme = if (isHttps) "wss" else "ws"
        val url = "$wsScheme://$host/realtime/v1/websocket?apikey=${supabaseClient.apiKey}&vsn=1.0.0"
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope
        pendingHeartbeatRef.value = null
        // Bound the handshake so a stalled connect surfaces as a timeout (and
        // triggers reconnect/Failed) instead of hanging on the platform default.
        val ws = withTimeout(config.connectionTimeoutMs) { httpClient.webSocketSession(url) }
        session = ws
        newScope.launch {
            try {
                for (frame in ws.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = json.decodeFromString<RealtimeMessage>(text)
                        recordInboundMessage(message)
                        dispatchToSubscription(message)
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
            } finally {
                if (!intentionalDisconnect) {
                    session = null
                    handleUnexpectedDisconnect()
                }
            }
        }
        heartbeatJob =
            newScope.launch {
                while (isActive) {
                    delay(config.heartbeatIntervalMs)
                    if (pendingHeartbeatRef.getAndSet(null) != null) {
                        // Previous heartbeat was never acknowledged → server is gone.
                        // Closing the socket wakes the incoming loop, whose finally
                        // block runs the reconnect path. getAndSet clears and reads
                        // atomically, so a reply landing concurrently can't be missed.
                        session?.close()
                        break
                    }
                    sendHeartbeatMessage()
                }
            }
    }

    private suspend fun sendHeartbeatMessage() {
        val ref = nextRef()
        pendingHeartbeatRef.value = ref
        sendMessage(
            RealtimeMessage(
                topic = "phoenix",
                event = "heartbeat",
                payload = buildJsonObject {},
                ref = ref,
            ),
        )
    }

    private fun recordOutboundMessage(message: RealtimeMessage) {
        // Senders run from arbitrary coroutines, so read-modify-write the counter
        // atomically to avoid lost updates.
        _debugState.update {
            it.copy(
                outboundMessageCount = it.outboundMessageCount + 1,
                heartbeatSentCount = it.heartbeatSentCount + if (message.isHeartbeatRequest()) 1 else 0,
                lastOutboundRef = message.ref,
            )
        }
        _debugEvents.tryEmit(RealtimeDebugEvent.OutboundMessage(message))
        if (message.isHeartbeatRequest()) {
            _debugEvents.tryEmit(RealtimeDebugEvent.HeartbeatSent(message.ref ?: ""))
        }
    }

    private fun recordInboundMessage(message: RealtimeMessage) {
        _debugState.update {
            it.copy(
                inboundMessageCount = it.inboundMessageCount + 1,
                heartbeatReceivedCount = it.heartbeatReceivedCount + if (message.isHeartbeatReply()) 1 else 0,
                lastInboundRef = message.ref,
            )
        }
        _debugEvents.tryEmit(RealtimeDebugEvent.InboundMessage(message))
        if (message.isHeartbeatReply()) {
            pendingHeartbeatRef.value = null
            _debugEvents.tryEmit(RealtimeDebugEvent.HeartbeatReceived(message.ref))
        }
    }

    private fun handleUnexpectedDisconnect() {
        if (!config.autoReconnect || intentionalDisconnect) return
        heartbeatJob?.cancel()
        heartbeatJob = null
        scope?.cancel()
        scope = null
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        // A disconnect() may have raced in (e.g. via the attemptReconnect catch path,
        // which isn't otherwise guarded). Never resurrect the socket the user asked to
        // tear down.
        if (intentionalDisconnect) return
        // Cancel any in-flight reconnect so overlapping triggers (e.g. a connect
        // failure racing an unexpected disconnect) don't spawn parallel loops.
        reconnectJob?.cancel()
        reconnectJob = null
        val maxAttempts = config.maxReconnectAttempts
        if (maxAttempts > 0 && reconnectAttempt.value >= maxAttempts) {
            _connectionState.value =
                ConnectionState.Failed(
                    reason = "Maximum reconnect attempts ($maxAttempts) exhausted",
                    attempts = reconnectAttempt.value,
                )
            return
        }
        val delayMs = calculateBackoffDelay(reconnectAttempt.value)
        _connectionState.value =
            ConnectionState.Reconnecting(
                attempt = reconnectAttempt.value + 1,
                nextRetryMs = delayMs,
            )
        reconnectJob =
            reconnectScope.launch {
                delay(delayMs)
                // disconnect() during the backoff delay cancels this job, but if it
                // landed just after this job was scheduled, re-check before reconnecting.
                if (intentionalDisconnect) return@launch
                reconnectAttempt.incrementAndGet()
                attemptReconnect()
            }
    }

    private suspend fun attemptReconnect() {
        _connectionState.value = ConnectionState.Connecting
        try {
            establishConnection()
            reconnectAttempt.value = 0
            _connectionState.value = ConnectionState.Connected
            rejoinActiveChannels()
            flushOutboundBuffer()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            session = null
            scope?.cancel()
            scope = null
            scheduleReconnect()
        }
    }

    private suspend fun rejoinActiveChannels() {
        val subscriptions = synchronized(subscriptionsLock) { activeSubscriptions.values.toList() }
        for (subscription in subscriptions) {
            sendJoinMessage(subscription)
        }
    }

    private suspend fun sendJoinMessage(
        subscription: ChannelSubscriptionImpl,
    ) {
        val topic = subscription.topic
        val postgresCallbacks = subscription.postgresCallbacks
        val receiveOwnBroadcasts = subscription.receiveOwnBroadcasts
        val acknowledgeBroadcasts = subscription.acknowledgeBroadcasts
        val presenceKey = subscription.presenceKey
        val privateChannel = subscription.privateChannel
        val replaySinceMs = subscription.replaySinceMs
        val replayLimit = subscription.replayLimit
        val postgresConfigs =
            postgresCallbacks.map { config ->
                buildJsonObject {
                    put("event", config.event.toWireValue())
                    put("schema", config.schema)
                    config.table?.let { put("table", it) }
                    config.filter?.let { put("filter", it) }
                }
            }
        val joinPayload =
            buildJsonObject {
                put(
                    "config",
                    buildJsonObject {
                        put(
                            "broadcast",
                            buildJsonObject {
                                put("self", JsonPrimitive(receiveOwnBroadcasts))
                                put("ack", JsonPrimitive(acknowledgeBroadcasts))
                                if (replaySinceMs != null) {
                                    put(
                                        "replay",
                                        buildJsonObject {
                                            put("since", JsonPrimitive(replaySinceMs))
                                            replayLimit?.let { put("limit", JsonPrimitive(it)) }
                                        },
                                    )
                                }
                            },
                        )
                        put(
                            "presence",
                            buildJsonObject {
                                put("enabled", JsonPrimitive(presenceKey.isNotEmpty() || subscription.hasPresenceTracking))
                                put("key", JsonPrimitive(presenceKey))
                            },
                        )
                        if (postgresConfigs.isNotEmpty()) {
                            put("postgres_changes", kotlinx.serialization.json.JsonArray(postgresConfigs))
                        }
                        put("private", JsonPrimitive(privateChannel))
                    },
                )
                put("access_token", JsonPrimitive(currentAccessToken()))
            }
        val joinRef = nextRef()
        subscription.joinRef = joinRef
        sendMessage(
            RealtimeMessage(
                topic = topic,
                event = "phx_join",
                payload = joinPayload,
                joinRef = joinRef,
                ref = joinRef,
            ),
        )
        // phx_join is fire-and-forget; without a watchdog a missing phx_reply
        // leaves the subscription stuck in SUBSCRIBING forever. Fail it on timeout.
        scope?.launch {
            val resolved =
                withTimeoutOrNull(config.connectionTimeoutMs) {
                    subscription.status.first { it != RealtimeSubscription.Status.SUBSCRIBING }
                }
            if (resolved == null) subscription.markJoinTimedOut()
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val delay =
            config.initialReconnectDelayMs *
                config.backoffMultiplier.pow(attempt.toDouble())
        val capped = min(delay.toLong(), config.maxReconnectDelayMs)
        if (!config.reconnectJitter) return capped
        // Equal jitter: keep half the delay deterministic (so we never stampede
        // immediately) and randomize the other half across [half, capped].
        val half = capped / 2
        return half + Random.nextLong(capped - half + 1)
    }

    private suspend fun dispatchToSubscription(message: RealtimeMessage) {
        val subscription = synchronized(subscriptionsLock) { activeSubscriptions[message.topic] } ?: return
        try {
            subscription.handleMessage(message)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Isolate failures to prevent one subscription from killing the
            // entire WebSocket connection. The subscription is left in its
            // current state so it can be cleaned up by the caller.
        }
    }

    private fun currentAccessToken(): String =
        authTokenOverride ?: supabaseClient.accessTokenOrNull ?: supabaseClient.apiKey
}

internal class ChannelSubscriptionImpl(
    override val channel: String,
    internal val topic: String,
    private val client: RealtimeClientImpl,
    internal val postgresCallbacks: List<PostgresCallbackConfig>,
    private val broadcastCallbacks: Map<String, suspend (JsonObject) -> Unit>,
    private val presenceCallback: (suspend (PresenceState) -> Unit)?,
    internal val receiveOwnBroadcasts: Boolean,
    internal val acknowledgeBroadcasts: Boolean,
    internal val presenceKey: String,
    internal val privateChannel: Boolean,
    internal val replaySinceMs: Long?,
    internal val replayLimit: Int?,
) : RealtimeSubscription {
    private val eventFlow = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    private val _status = MutableStateFlow(RealtimeSubscription.Status.SUBSCRIBING)

    // Cumulative presence state, mutated only on the single inbound dispatch loop.
    private val presenceMembers = mutableMapOf<String, JsonObject>()

    // Immutable snapshot of [presenceMembers], republished on every change so
    // presenceState() can be read safely from any thread.
    @Volatile
    private var presenceSnapshot: PresenceState = emptyMap()

    // Server-assigned postgres_changes ids -> the local callback config that
    // produced them. Populated from the join phx_reply (`response.postgres_changes`),
    // which echoes the bindings in the order we sent them. Used to route inbound
    // change events by their `ids` so two filters on the same table that differ
    // only by `filter` don't both fire. Mutated only on the single inbound loop.
    private val serverBindingsById = mutableMapOf<Long, PostgresCallbackConfig>()
    internal var joinRef: String? = null
    internal val hasPresenceTracking: Boolean = presenceCallback != null
    override val status: StateFlow<RealtimeSubscription.Status> = _status.asStateFlow()

    override fun asFlow(): Flow<RealtimeEvent> = eventFlow

    override fun presenceState(): PresenceState = presenceSnapshot

    internal fun markJoinTimedOut() {
        if (_status.value == RealtimeSubscription.Status.SUBSCRIBING) {
            _status.value = RealtimeSubscription.Status.ERROR
        }
    }

    override suspend fun unsubscribe() {
        _status.value = RealtimeSubscription.Status.UNSUBSCRIBING
        client.leaveChannel(topic)
        _status.value = RealtimeSubscription.Status.UNSUBSCRIBED
    }

    override suspend fun send(type: RealtimeSubscription.SendType, event: String, payload: JsonObject?) {
        client.sendMessage(
            RealtimeMessage(
                topic = topic,
                event = type.wireValue,
                payload =
                    buildJsonObject {
                        put("type", type.wireValue)
                        put("event", event)
                        if (payload != null) {
                            put("payload", payload)
                        }
                    },
                joinRef = joinRef,
                ref = client.nextRef(),
            ),
        )
    }

    override suspend fun broadcast(event: String, payload: JsonObject) {
        send(RealtimeSubscription.SendType.BROADCAST, event, payload)
    }

    override suspend fun track(state: JsonObject) {
        send(RealtimeSubscription.SendType.PRESENCE, "track", state)
    }

    override suspend fun untrack() {
        send(RealtimeSubscription.SendType.PRESENCE, "untrack")
    }

    internal suspend fun handleMessage(message: RealtimeMessage) {
        when (message.event) {
            "postgres_changes" -> handlePostgresChange(message.payload)
            "broadcast" -> handleBroadcast(message.payload)
            "presence_diff" -> handlePresenceDiff(message.payload)
            "presence_state" -> handlePresenceState(message.payload)
            "phx_reply" -> handleSystemReply(message.payload, message.ref)
            "phx_error" -> {
                _status.value = RealtimeSubscription.Status.ERROR
                val event =
                    RealtimeEvent.SystemEvent(
                        status = "error",
                        message = message.payload.toString(),
                    )
                eventFlow.emit(event)
            }
            else -> { }
        }
    }

    private suspend fun handlePostgresChange(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return
        val type = data["type"]?.jsonPrimitive?.content ?: return
        val record = data["record"]?.jsonObject
        val oldRecord = data["old_record"]?.jsonObject
        val event = buildPostgresEvent(type, record, oldRecord) ?: return
        eventFlow.emit(event)
        val callbackPayload = record ?: oldRecord ?: return
        for (config in resolvePostgresConfigs(payload, type)) {
            dispatchPostgresChange(config, type, callbackPayload)
        }
    }

    private fun buildPostgresEvent(type: String, record: JsonObject?, oldRecord: JsonObject?): RealtimeEvent? =
        when (type) {
            "INSERT" -> record?.let { RealtimeEvent.PostgresInsert(record = it, oldRecord = oldRecord) }
            "UPDATE" -> record?.let { RealtimeEvent.PostgresUpdate(record = it, oldRecord = oldRecord) }
            "DELETE" -> oldRecord?.let { RealtimeEvent.PostgresDelete(oldRecord = it) }
            else -> null
        }

    // Prefer routing by the server-assigned binding ids carried on the event: this correctly
    // distinguishes two subscriptions on the same table that differ only by `filter`. Fall back to
    // event-type matching when the server omits `ids` or we never captured bindings (older servers).
    private fun resolvePostgresConfigs(payload: JsonObject, type: String): List<PostgresCallbackConfig> {
        val ids = payload["ids"] as? JsonArray
        return if (ids != null && serverBindingsById.isNotEmpty()) {
            ids.mapNotNull {
                it.jsonPrimitive.content
                    .toLongOrNull()
                    ?.let(serverBindingsById::get)
            }
        } else {
            postgresCallbacks.filter { it.event == PostgresChangeEvent.ALL || it.event.toWireValue() == type }
        }
    }

    private suspend fun dispatchPostgresChange(config: PostgresCallbackConfig, type: String, callbackPayload: JsonObject) {
        when (config) {
            is PostgresCallbackConfig.Simple -> config.callback(callbackPayload)
            is PostgresCallbackConfig.Typed -> {
                val changeEvent =
                    try {
                        PostgresChangeEvent.valueOf(type)
                    } catch (_: IllegalArgumentException) {
                        PostgresChangeEvent.ALL
                    }
                config.callback(changeEvent, callbackPayload)
            }
        }
    }

    private suspend fun handleBroadcast(payload: JsonObject) {
        val eventName = payload["event"]?.jsonPrimitive?.content ?: return
        val broadcastPayload = payload["payload"]?.jsonObject ?: buildJsonObject {}
        val event = RealtimeEvent.Broadcast(event = eventName, payload = broadcastPayload)
        eventFlow.emit(event)
        broadcastCallbacks[eventName]?.invoke(broadcastPayload)
    }

    private suspend fun handlePresenceDiff(payload: JsonObject) {
        val joins = payload["joins"]?.jsonObject
        val leaves = payload["leaves"]?.jsonObject
        joins?.forEach { (key, value) ->
            val presence = unwrapPresence(value.jsonObject)
            presenceMembers[key] = presence
            eventFlow.emit(RealtimeEvent.PresenceJoin(key = key, newPresence = presence))
        }
        leaves?.forEach { (key, value) ->
            val presence = unwrapPresence(value.jsonObject)
            presenceMembers.remove(key)
            eventFlow.emit(RealtimeEvent.PresenceLeave(key = key, leftPresence = presence))
        }
        // Hand the callback the full, cumulative presence state — not just the
        // members in this diff — so callers always see who is currently present.
        val state: PresenceState = presenceMembers.toMap()
        presenceSnapshot = state
        presenceCallback?.invoke(state)
    }

    private suspend fun handlePresenceState(payload: JsonObject) {
        presenceMembers.clear()
        payload.forEach { (key, value) -> presenceMembers[key] = unwrapPresence(value.jsonObject) }
        val state: PresenceState = presenceMembers.toMap()
        presenceSnapshot = state
        eventFlow.emit(RealtimeEvent.PresenceSync(state = state))
        presenceCallback?.invoke(state)
    }

    private suspend fun handleSystemReply(payload: JsonObject, ref: String?) {
        val status = payload["status"]?.jsonPrimitive?.content ?: "ok"
        val response = payload["response"]
        // Only the reply to our own phx_join (ref == joinRef) drives subscription
        // status and binding capture. A later `ok` reply to phx_leave (which uses a
        // different ref) must not resurrect a channel we've already left, and a stray
        // ack for a broadcast/presence push must not flip status either.
        if (ref != null && ref == joinRef) {
            _status.value =
                if (status == "ok") {
                    RealtimeSubscription.Status.SUBSCRIBED
                } else {
                    RealtimeSubscription.Status.ERROR
                }
            if (status == "ok" && response is JsonObject) {
                captureServerBindings(response)
            }
        }
        val event =
            RealtimeEvent.SystemEvent(
                status = status,
                message = response?.toString(),
            )
        eventFlow.emit(event)
    }

    // The join reply echoes `postgres_changes: [{id, event, schema, table, filter}]`
    // in the same order we registered them, so we can pair each server id with the
    // local callback at the same index. Rebuilt on every (re)join.
    private fun captureServerBindings(response: JsonObject) {
        val bindings = (response["postgres_changes"] as? JsonArray) ?: return
        serverBindingsById.clear()
        bindings.forEachIndexed { index, element ->
            val id =
                (element as? JsonObject)
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.content
                    ?.toLongOrNull() ?: return@forEachIndexed
            postgresCallbacks.getOrNull(index)?.let { serverBindingsById[id] = it }
        }
    }
}

internal fun PostgresChangeEvent.toWireValue(): String =
    when (this) {
        PostgresChangeEvent.INSERT -> "INSERT"
        PostgresChangeEvent.UPDATE -> "UPDATE"
        PostgresChangeEvent.DELETE -> "DELETE"
        PostgresChangeEvent.ALL -> "*"
    }

// The realtime wire wraps each presence entry as `{"metas":[{phx_ref, ...state}]}`.
// Callers want the user state, not the envelope, so lift the first meta entry and
// drop the server-internal `phx_ref`. Returns the object unchanged if there is no
// `metas` array (e.g. an already-flattened payload), so this is safe to apply
// unconditionally.
private fun unwrapPresence(entry: JsonObject): JsonObject {
    val metas = entry["metas"] as? JsonArray ?: return entry
    val first = metas.firstOrNull() as? JsonObject ?: return buildJsonObject {}
    return buildJsonObject {
        first.forEach { (k, v) -> if (k != "phx_ref") put(k, v) }
    }
}

// Control frames that are regenerated on reconnect and must not be replayed
// from the outbound buffer (they would carry stale refs / duplicate joins).
private val NON_BUFFERED_EVENTS = setOf("heartbeat", "phx_join", "phx_leave")

private fun RealtimeMessage.isHeartbeatRequest(): Boolean =
    topic == "phoenix" && event == "heartbeat"

private fun RealtimeMessage.isHeartbeatReply(): Boolean =
    topic == "phoenix" && event == "phx_reply"
