package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
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

/**
 * Ktor WebSocket-based implementation of [RealtimeClient].
 *
 * Communicates with the Supabase Realtime server using the Phoenix
 * WebSocket protocol. Maintains a heartbeat, dispatches incoming
 * messages to per-channel flows, and manages channel join/leave lifecycle.
 *
 * Supports automatic reconnection with exponential backoff when the
 * connection drops unexpectedly.
 */
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

    /**
     * When true, the disconnect was initiated by the user (via [disconnect]),
     * so auto-reconnect should not kick in.
     */
    private var intentionalDisconnect = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val isConnected: Boolean get() = _connectionState.value is ConnectionState.Connected

    /** All incoming messages are fanned out through this shared flow. */
    private val incomingMessages = MutableSharedFlow<RealtimeMessage>(extraBufferCapacity = 64)

    /** Tracks active channel subscriptions by topic. */
    private val activeSubscriptions = mutableMapOf<String, ChannelSubscriptionImpl>()

    internal fun nextRef(): String = (++refCounter).toString()

    // ── RealtimeClient ──────────────────────────────────────────────────

    override fun channel(name: String): RealtimeChannelBuilder =
        RealtimeChannelBuilder(channelName = name, client = this)

    override suspend fun connect() {
        if (session != null) return

        intentionalDisconnect = false
        _connectionState.value = ConnectionState.Connecting

        try {
            establishConnection()
            reconnectAttempt = 0
            _connectionState.value = ConnectionState.Connected
        } catch (e: Exception) {
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

        // Cancel any pending reconnect
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0

        // Leave all channels
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

    // ── Internal API ────────────────────────────────────────────────────

    internal suspend fun subscribe(builder: RealtimeChannelBuilder): RealtimeSubscription {
        val topic = "realtime:${builder.channelName}"
        val subscription = ChannelSubscriptionImpl(
            channel = builder.channelName,
            topic = topic,
            client = this,
            postgresCallbacks = builder.postgresCallbacks.toList(),
            broadcastCallbacks = builder.broadcastCallbacks.toMap(),
            presenceCallback = builder.presenceCallback,
        )
        activeSubscriptions[topic] = subscription

        sendJoinMessage(topic, builder.postgresCallbacks)

        return subscription
    }

    internal suspend fun leaveChannel(topic: String) {
        activeSubscriptions.remove(topic)
        sendMessage(
            RealtimeMessage(
                topic = topic,
                event = "phx_leave",
                payload = buildJsonObject {},
                ref = nextRef(),
            ),
        )
    }

    internal suspend fun sendMessage(message: RealtimeMessage) {
        val text = json.encodeToString(message)
        session?.send(Frame.Text(text))
    }

    // ── Connection management ───────────────────────────────────────────

    private suspend fun establishConnection() {
        val host = supabaseClient.projectUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val url = "wss://$host/realtime/v1/websocket?apikey=${supabaseClient.apiKey}&vsn=1.0.0"

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        val ws = httpClient.webSocketSession(url)
        session = ws

        // Receive loop
        newScope.launch {
            try {
                for (frame in ws.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = json.decodeFromString<RealtimeMessage>(text)
                        incomingMessages.emit(message)
                        dispatchToSubscription(message)
                    }
                }
            } catch (_: Exception) {
                // Connection closed or error
            } finally {
                // If the receive loop ends and we didn't intend to disconnect,
                // the connection dropped — trigger reconnection.
                if (!intentionalDisconnect) {
                    session = null
                    handleUnexpectedDisconnect()
                }
            }
        }

        // Heartbeat loop
        heartbeatJob = newScope.launch {
            while (isActive) {
                delay(config.heartbeatIntervalMs)
                sendMessage(
                    RealtimeMessage(
                        topic = "phoenix",
                        event = "heartbeat",
                        payload = buildJsonObject {},
                        ref = nextRef(),
                    ),
                )
            }
        }
    }

    private fun handleUnexpectedDisconnect() {
        if (!config.autoReconnect || intentionalDisconnect) return

        // Clean up current scope without touching subscriptions (we want to re-join them)
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

        // Use a fresh scope for the reconnect job since the old scope is cancelled
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

            // Re-subscribe all active channels
            rejoinActiveChannels()
        } catch (_: Exception) {
            session = null
            scope?.cancel()
            scope = null
            scheduleReconnect()
        }
    }

    /**
     * After a successful reconnect, re-sends `phx_join` for every channel
     * that was active before the connection dropped.
     */
    private suspend fun rejoinActiveChannels() {
        for ((_, subscription) in activeSubscriptions) {
            sendJoinMessage(subscription.topic, subscription.postgresCallbacks)
        }
    }

    /**
     * Sends a `phx_join` message for the given topic with Postgres change configs.
     */
    private suspend fun sendJoinMessage(
        topic: String,
        postgresCallbacks: List<PostgresCallbackConfig>,
    ) {
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
                    put("self", JsonPrimitive(false))
                })
                put("presence", buildJsonObject {
                    put("key", JsonPrimitive(""))
                })
                if (postgresConfigs.isNotEmpty()) {
                    put("postgres_changes", kotlinx.serialization.json.JsonArray(postgresConfigs))
                }
            })
            put("access_token", JsonPrimitive(supabaseClient.apiKey))
        }

        sendMessage(
            RealtimeMessage(
                topic = topic,
                event = "phx_join",
                payload = joinPayload,
                ref = nextRef(),
            ),
        )
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val delay = config.initialReconnectDelayMs *
            config.backoffMultiplier.pow(attempt.toDouble())
        return min(delay.toLong(), config.maxReconnectDelayMs)
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private suspend fun dispatchToSubscription(message: RealtimeMessage) {
        val subscription = activeSubscriptions[message.topic] ?: return
        subscription.handleMessage(message)
    }
}

/**
 * Live subscription bound to a single Realtime channel.
 */
internal class ChannelSubscriptionImpl(
    override val channel: String,
    internal val topic: String,
    private val client: RealtimeClientImpl,
    internal val postgresCallbacks: List<PostgresCallbackConfig>,
    private val broadcastCallbacks: Map<String, suspend (JsonObject) -> Unit>,
    private val presenceCallback: (suspend (PresenceState) -> Unit)?,
) : RealtimeSubscription {

    private val eventFlow = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)

    override fun asFlow(): Flow<RealtimeEvent> = eventFlow

    override suspend fun unsubscribe() {
        client.leaveChannel(topic)
    }

    override suspend fun broadcast(event: String, payload: JsonObject) {
        client.sendMessage(
            RealtimeMessage(
                topic = topic,
                event = "broadcast",
                payload = buildJsonObject {
                    put("type", "broadcast")
                    put("event", event)
                    put("payload", payload)
                },
                ref = client.nextRef(),
            ),
        )
    }

    override suspend fun track(state: JsonObject) {
        client.sendMessage(
            RealtimeMessage(
                topic = topic,
                event = "presence",
                payload = buildJsonObject {
                    put("type", "presence")
                    put("event", "track")
                    put("payload", state)
                },
                ref = client.nextRef(),
            ),
        )
    }

    override suspend fun untrack() {
        client.sendMessage(
            RealtimeMessage(
                topic = topic,
                event = "presence",
                payload = buildJsonObject {
                    put("type", "presence")
                    put("event", "untrack")
                },
                ref = client.nextRef(),
            ),
        )
    }

    /**
     * Routes an incoming server message to the appropriate callback and
     * emits the corresponding [RealtimeEvent] on [eventFlow].
     */
    internal suspend fun handleMessage(message: RealtimeMessage) {
        when (message.event) {
            "postgres_changes" -> handlePostgresChange(message.payload)
            "broadcast" -> handleBroadcast(message.payload)
            "presence_diff" -> handlePresenceDiff(message.payload)
            "presence_state" -> handlePresenceState(message.payload)
            "phx_reply" -> handleSystemReply(message.payload)
            "phx_error" -> {
                val event = RealtimeEvent.SystemEvent(
                    status = "error",
                    message = message.payload.toString(),
                )
                eventFlow.emit(event)
            }
            else -> { /* Ignore unrecognised events */ }
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

        // Invoke registered callbacks
        for (config in postgresCallbacks) {
            if (config.event == PostgresChangeEvent.ALL || config.event.toWireValue() == type) {
                val callbackPayload = record ?: oldRecord ?: return
                config.callback(callbackPayload)
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

        // Notify presence callback with the full diff as a combined state snapshot
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
        val response = payload["response"]

        val event = RealtimeEvent.SystemEvent(
            status = status,
            message = response?.toString(),
        )
        eventFlow.emit(event)
    }
}

/**
 * Maps [PostgresChangeEvent] to the wire-format string expected by the server.
 */
internal fun PostgresChangeEvent.toWireValue(): String = when (this) {
    PostgresChangeEvent.INSERT -> "INSERT"
    PostgresChangeEvent.UPDATE -> "UPDATE"
    PostgresChangeEvent.DELETE -> "DELETE"
    PostgresChangeEvent.ALL -> "*"
}
