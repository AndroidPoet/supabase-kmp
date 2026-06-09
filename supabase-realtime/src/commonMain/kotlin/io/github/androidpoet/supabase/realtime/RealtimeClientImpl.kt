package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.min
import kotlin.math.pow
internal class RealtimeClientImpl(
    private val supabaseClient: SupabaseClient,
    private val config: RealtimeConfig = RealtimeConfig(),
) : RealtimeClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient {
        install(WebSockets)
    }
    private var session: WebSocketSession? = null
    private var scope: CoroutineScope? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var refCounter = 0
    private var reconnectAttempt = 0
    private var authTokenOverride: String? = null
    private var intentionalDisconnect = false
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _debugState = MutableStateFlow(RealtimeDebugState())
    override val debugState: StateFlow<RealtimeDebugState> = _debugState.asStateFlow()
    private val _debugEvents = MutableSharedFlow<RealtimeDebugEvent>(extraBufferCapacity = 64)
    override val debugEvents: Flow<RealtimeDebugEvent> = _debugEvents
    override val isConnected: Boolean get() = _connectionState.value is ConnectionState.Connected
    override val isConnecting: Boolean get() = _connectionState.value is ConnectionState.Connecting
    override val isDisconnecting: Boolean get() = _connectionState.value is ConnectionState.Disconnecting
    private val activeSubscriptions = mutableMapOf<String, ChannelSubscriptionImpl>()
    internal fun nextRef(): String = (++refCounter).toString()
    override fun channel(name: String): RealtimeChannelBuilder =
        RealtimeChannelBuilder(channelName = name, client = this)
    override fun getSubscription(name: String): RealtimeSubscription? =
        activeSubscriptions["realtime:$name"]
    override fun getSubscriptionByTopic(topic: String): RealtimeSubscription? =
        activeSubscriptions[topic]
    override fun getSubscriptions(): Set<RealtimeSubscription> =
        activeSubscriptions.values.toSet()
    override fun activeChannels(): Set<String> =
        activeSubscriptions.values.mapTo(mutableSetOf()) { it.channel }
    override fun activeChannelDetails(): Set<RealtimeChannel> =
        activeSubscriptions.values.mapTo(mutableSetOf()) { RealtimeChannel(name = it.channel, topic = it.topic) }
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
        activeSubscriptions[topic]?.unsubscribe()
    }
    override suspend fun removeChannelsByTopic(topics: List<String>) {
        topics.forEach { topic ->
            activeSubscriptions[topic]?.unsubscribe()
        }
    }
    override suspend fun removeChannel(name: String) {
        val topic = "realtime:$name"
        activeSubscriptions[topic]?.unsubscribe()
    }
    override suspend fun removeAllChannels() {
        activeSubscriptions.values.toList().forEach { it.unsubscribe() }
    }
    override suspend fun setAuth(token: String?) {
        authTokenOverride = token
        if (activeSubscriptions.isEmpty()) return
        val accessToken = currentAccessToken()
        activeSubscriptions.values.forEach { subscription ->
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
        if (session != null) return
        intentionalDisconnect = false
        _connectionState.value = ConnectionState.Connecting
        try {
            establishConnection()
            reconnectAttempt = 0
            _connectionState.value = ConnectionState.Connected
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            session = null
            if (config.autoReconnect && !intentionalDisconnect) {
                scheduleReconnect()
            } else {
                _connectionState.value = ConnectionState.Failed(
                    reason = e.message ?: "Connection failed",
                    attempts = reconnectAttempt,
                )
            }
        }
    }
    override suspend fun disconnect() {
        intentionalDisconnect = true
        _connectionState.value = ConnectionState.Disconnecting
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        activeSubscriptions.values.toList().forEach { it.unsubscribe() }
        activeSubscriptions.clear()
        heartbeatJob?.cancel()
        heartbeatJob = null
        session?.close()
        session = null
        scope?.cancel()
        scope = null
        _connectionState.value = ConnectionState.Disconnected
    }
    internal suspend fun subscribe(builder: RealtimeChannelBuilder): RealtimeSubscription {
        val topic = "realtime:${builder.channelName}"
        val subscription = ChannelSubscriptionImpl(
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
        activeSubscriptions[topic] = subscription
        sendJoinMessage(subscription)
        return subscription
    }
    internal suspend fun leaveChannel(topic: String) {
        val subscription = activeSubscriptions.remove(topic)
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
        val text = json.encodeToString(message)
        recordOutboundMessage(message)
        session?.send(Frame.Text(text))
    }
    private suspend fun establishConnection() {
        val isHttps = supabaseClient.projectUrl.startsWith("https://")
        val host = supabaseClient.projectUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val wsScheme = if (isHttps) "wss" else "ws"
        val url = "$wsScheme://$host/realtime/v1/websocket?apikey=${supabaseClient.apiKey}&vsn=1.0.0"
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope
        val ws = httpClient.webSocketSession(url)
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
        heartbeatJob = newScope.launch {
            while (isActive) {
                delay(config.heartbeatIntervalMs)
                sendHeartbeatMessage()
            }
        }
    }
    private suspend fun sendHeartbeatMessage() {
        sendMessage(
            RealtimeMessage(
                topic = "phoenix",
                event = "heartbeat",
                payload = buildJsonObject {},
                ref = nextRef(),
            ),
        )
    }
    private fun recordOutboundMessage(message: RealtimeMessage) {
        _debugState.value = _debugState.value.copy(
            outboundMessageCount = _debugState.value.outboundMessageCount + 1,
            heartbeatSentCount = _debugState.value.heartbeatSentCount + if (message.isHeartbeatRequest()) 1 else 0,
            lastOutboundRef = message.ref,
        )
        _debugEvents.tryEmit(RealtimeDebugEvent.OutboundMessage(message))
        if (message.isHeartbeatRequest()) {
            _debugEvents.tryEmit(RealtimeDebugEvent.HeartbeatSent(message.ref ?: ""))
        }
    }
    private fun recordInboundMessage(message: RealtimeMessage) {
        _debugState.value = _debugState.value.copy(
            inboundMessageCount = _debugState.value.inboundMessageCount + 1,
            heartbeatReceivedCount = _debugState.value.heartbeatReceivedCount + if (message.isHeartbeatReply()) 1 else 0,
            lastInboundRef = message.ref,
        )
        _debugEvents.tryEmit(RealtimeDebugEvent.InboundMessage(message))
        if (message.isHeartbeatReply()) {
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
        val maxAttempts = config.maxReconnectAttempts
        if (maxAttempts > 0 && reconnectAttempt >= maxAttempts) {
            _connectionState.value = ConnectionState.Failed(
                reason = "Maximum reconnect attempts ($maxAttempts) exhausted",
                attempts = reconnectAttempt,
            )
            return
        }
        val delayMs = calculateBackoffDelay(reconnectAttempt)
        _connectionState.value = ConnectionState.Reconnecting(
            attempt = reconnectAttempt + 1,
            nextRetryMs = delayMs,
        )
        val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        reconnectJob = reconnectScope.launch {
            delay(delayMs)
            reconnectAttempt++
            attemptReconnect()
        }
    }
    private suspend fun attemptReconnect() {
        _connectionState.value = ConnectionState.Connecting
        try {
            establishConnection()
            reconnectAttempt = 0
            _connectionState.value = ConnectionState.Connected
            rejoinActiveChannels()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            session = null
            scope?.cancel()
            scope = null
            scheduleReconnect()
        }
    }
    private suspend fun rejoinActiveChannels() {
        for ((_, subscription) in activeSubscriptions) {
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
        val postgresConfigs = postgresCallbacks.map { config ->
            buildJsonObject {
                put("event", config.event.toWireValue())
                put("schema", config.schema)
                config.table?.let { put("table", it) }
                config.filter?.let { put("filter", it) }
            }
        }
        val joinPayload = buildJsonObject {
            put("config", buildJsonObject {
                put("broadcast", buildJsonObject {
                    put("self", JsonPrimitive(receiveOwnBroadcasts))
                    put("ack", JsonPrimitive(acknowledgeBroadcasts))
                    if (replaySinceMs != null) {
                        put("replay", buildJsonObject {
                            put("since", JsonPrimitive(replaySinceMs))
                            replayLimit?.let { put("limit", JsonPrimitive(it)) }
                        })
                    }
                })
                put("presence", buildJsonObject {
                    put("enabled", JsonPrimitive(presenceKey.isNotEmpty() || subscription.hasPresenceTracking))
                    put("key", JsonPrimitive(presenceKey))
                })
                if (postgresConfigs.isNotEmpty()) {
                    put("postgres_changes", kotlinx.serialization.json.JsonArray(postgresConfigs))
                }
                put("private", JsonPrimitive(privateChannel))
            })
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
    }
    private fun calculateBackoffDelay(attempt: Int): Long {
        val delay = config.initialReconnectDelayMs *
            config.backoffMultiplier.pow(attempt.toDouble())
        return min(delay.toLong(), config.maxReconnectDelayMs)
    }
    private suspend fun dispatchToSubscription(message: RealtimeMessage) {
        val subscription = activeSubscriptions[message.topic] ?: return
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
    internal var joinRef: String? = null
    internal val hasPresenceTracking: Boolean = presenceCallback != null
    override val status: StateFlow<RealtimeSubscription.Status> = _status.asStateFlow()
    override fun asFlow(): Flow<RealtimeEvent> = eventFlow
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
                payload = buildJsonObject {
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
            "phx_reply" -> handleSystemReply(message.payload)
            "phx_error" -> {
                _status.value = RealtimeSubscription.Status.ERROR
                val event = RealtimeEvent.SystemEvent(
                    status = "error",
                    message = message.payload.toString(),
                )
                eventFlow.emit(event)
            }
            else -> {  }
        }
    }
    private suspend fun handlePostgresChange(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return
        val type = data["type"]?.jsonPrimitive?.content ?: return
        val record = data["record"]?.jsonObject
        val oldRecord = data["old_record"]?.jsonObject
        val event = when (type) {
            "INSERT" -> {
                record ?: return
                RealtimeEvent.PostgresInsert(record = record, oldRecord = oldRecord)
            }
            "UPDATE" -> {
                record ?: return
                RealtimeEvent.PostgresUpdate(record = record, oldRecord = oldRecord)
            }
            "DELETE" -> {
                val old = oldRecord ?: return
                RealtimeEvent.PostgresDelete(oldRecord = old)
            }
            else -> return
        }
        eventFlow.emit(event)
        for (config in postgresCallbacks) {
            if (config.event == PostgresChangeEvent.ALL || config.event.toWireValue() == type) {
                val callbackPayload = record ?: oldRecord ?: return
                when (config) {
                    is PostgresCallbackConfig.Simple -> config.callback(callbackPayload)
                    is PostgresCallbackConfig.Typed -> {
                        val changeEvent = try {
                            PostgresChangeEvent.valueOf(type)
                        } catch (_: IllegalArgumentException) {
                            PostgresChangeEvent.ALL
                        }
                        config.callback(changeEvent, callbackPayload)
                    }
                }
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
            val presence = value.jsonObject
            val event = RealtimeEvent.PresenceJoin(key = key, newPresence = presence)
            eventFlow.emit(event)
        }
        leaves?.forEach { (key, value) ->
            val presence = value.jsonObject
            val event = RealtimeEvent.PresenceLeave(key = key, leftPresence = presence)
            eventFlow.emit(event)
        }
        presenceCallback?.let { cb ->
            val state = buildMap {
                joins?.forEach { (k, v) -> put(k, v.jsonObject) }
            }
            if (state.isNotEmpty()) cb(state)
        }
    }
    private suspend fun handlePresenceState(payload: JsonObject) {
        val state: PresenceState = payload.mapValues { (_, value) -> value.jsonObject }
        val event = RealtimeEvent.PresenceSync(state = state)
        eventFlow.emit(event)
        presenceCallback?.invoke(state)
    }
    private suspend fun handleSystemReply(payload: JsonObject) {
        val status = payload["status"]?.jsonPrimitive?.content ?: "ok"
        _status.value = if (status == "ok") {
            RealtimeSubscription.Status.SUBSCRIBED
        } else {
            RealtimeSubscription.Status.ERROR
        }
        val response = payload["response"]
        val event = RealtimeEvent.SystemEvent(
            status = status,
            message = response?.toString(),
        )
        eventFlow.emit(event)
    }
}
internal fun PostgresChangeEvent.toWireValue(): String = when (this) {
    PostgresChangeEvent.INSERT -> "INSERT"
    PostgresChangeEvent.UPDATE -> "UPDATE"
    PostgresChangeEvent.DELETE -> "DELETE"
    PostgresChangeEvent.ALL -> "*"
}

private fun RealtimeMessage.isHeartbeatRequest(): Boolean =
    topic == "phoenix" && event == "heartbeat"

private fun RealtimeMessage.isHeartbeatReply(): Boolean =
    topic == "phoenix" && event == "phx_reply"
