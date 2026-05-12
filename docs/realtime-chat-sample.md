# Realtime Chat Sample (Jetchat-Style + Supabase)

This sample mirrors the structure used in modern Compose chat apps (UI state + unidirectional events), while using Supabase as backend for:

- auth and user identity
- paginated message history
- realtime message delivery
- typing/presence signals

## 1) Data model (Supabase)

Use three core tables:

- `chat_rooms`
- `chat_members`
- `chat_messages`

```sql
create table if not exists public.chat_rooms (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.chat_members (
  room_id uuid not null references public.chat_rooms(id) on delete cascade,
  user_id uuid not null,
  joined_at timestamptz not null default now(),
  primary key (room_id, user_id)
);

create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.chat_rooms(id) on delete cascade,
  sender_id uuid not null,
  body text not null,
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  deleted_at timestamptz
);

create index if not exists idx_chat_messages_room_created_at
  on public.chat_messages(room_id, created_at desc);
```

## 2) RLS policies

```sql
alter table public.chat_rooms enable row level security;
alter table public.chat_members enable row level security;
alter table public.chat_messages enable row level security;

create policy "members can read room"
on public.chat_rooms for select
using (
  exists (
    select 1 from public.chat_members m
    where m.room_id = chat_rooms.id and m.user_id = auth.uid()
  )
);

create policy "members can read messages"
on public.chat_messages for select
using (
  exists (
    select 1 from public.chat_members m
    where m.room_id = chat_messages.room_id and m.user_id = auth.uid()
  )
);

create policy "members can insert messages"
on public.chat_messages for insert
with check (
  sender_id = auth.uid() and
  exists (
    select 1 from public.chat_members m
    where m.room_id = chat_messages.room_id and m.user_id = auth.uid()
  )
);
```

## 3) Kotlin models

```kotlin
@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NewMessage(
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
)
```

## 4) Pagination strategy

Use descending `created_at`, fixed page size, and keyset cursor.

- initial page: newest N messages
- older page: `created_at < oldestLoadedCreatedAt`
- keep a `hasMore` flag from page size

```kotlin
suspend fun loadPage(
    roomId: String,
    beforeCreatedAt: String?,
    pageSize: Int = 40,
): SupabaseResult<List<ChatMessage>> {
    return database.selectTyped<ChatMessage>(table = "chat_messages") {
        eq("room_id", roomId)
        if (beforeCreatedAt != null) lt("created_at", beforeCreatedAt)
        order("created_at", ascending = false)
        limit(pageSize)
    }
}
```

## 5) Realtime subscription

Subscribe to inserts for one room and merge into UI list.

```kotlin
suspend fun subscribeToRoom(
    roomId: String,
    onInsert: suspend (ChatMessage) -> Unit,
): RealtimeSubscription {
    return realtime.channel("room:$roomId")
        .onPostgresChange(
            schema = "public",
            table = "chat_messages",
            filter = "room_id=eq.$roomId",
            event = PostgresChangeEvent.INSERT,
        ) { payload ->
            val message = json.decodeFromJsonElement<ChatMessage>(payload)
            onInsert(message)
        }
        .subscribe()
}
```

## 6) Repository (single source of truth)

```kotlin
class ChatRepository(
    private val database: DatabaseClient,
    private val realtime: RealtimeClient,
    private val json: Json,
) {
    private var sub: RealtimeSubscription? = null

    suspend fun openRoom(roomId: String, onIncoming: suspend (ChatMessage) -> Unit) {
        realtime.connect()
        sub = realtime.channel("room:$roomId")
            .onPostgresChange(
                schema = "public",
                table = "chat_messages",
                filter = "room_id=eq.$roomId",
                event = PostgresChangeEvent.INSERT,
            ) { payload ->
                onIncoming(json.decodeFromJsonElement(payload))
            }
            .subscribe()
    }

    suspend fun closeRoom() {
        sub?.unsubscribe()
        sub = null
        realtime.disconnect()
    }

    suspend fun sendMessage(roomId: String, senderId: String, text: String): SupabaseResult<Unit> {
        val body = json.encodeToString(NewMessage(roomId, senderId, text.trim()))
        return database.insert(table = "chat_messages", body = body).map { Unit }
    }
}
```

## 7) ViewModel state (Jetchat-like)

```kotlin
data class ChatUiState(
    val roomId: String,
    val messages: List<ChatMessage> = emptyList(),
    val composerText: String = "",
    val isLoadingHistory: Boolean = false,
    val hasMoreHistory: Boolean = true,
    val sending: Boolean = false,
    val error: String? = null,
)
```

```kotlin
class ChatViewModel(
    private val repo: ChatRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ChatUiState(roomId = ""))
    val ui: StateFlow<ChatUiState> = _ui

    fun init(roomId: String) {
        if (_ui.value.roomId.isNotEmpty()) return
        _ui.update { it.copy(roomId = roomId) }

        viewModelScope.launch {
            repo.openRoom(roomId) { incoming ->
                _ui.update { state ->
                    if (state.messages.any { it.id == incoming.id }) state
                    else state.copy(messages = listOf(incoming) + state.messages)
                }
            }
            loadInitial()
        }
    }

    fun onComposerChanged(value: String) {
        _ui.update { it.copy(composerText = value) }
    }

    fun send(myUserId: String) {
        val state = _ui.value
        val text = state.composerText.trim()
        if (text.isEmpty() || state.sending) return

        viewModelScope.launch {
            _ui.update { it.copy(sending = true) }
            val result = repo.sendMessage(state.roomId, myUserId, text)
            _ui.update {
                when (result) {
                    is SupabaseResult.Success -> it.copy(composerText = "", sending = false)
                    is SupabaseResult.Failure -> it.copy(sending = false, error = result.error.message)
                }
            }
        }
    }

    fun loadOlder() {
        val state = _ui.value
        if (state.isLoadingHistory || !state.hasMoreHistory || state.messages.isEmpty()) return

        viewModelScope.launch {
            _ui.update { it.copy(isLoadingHistory = true) }
            val oldest = state.messages.last().createdAt
            when (val page = repo.loadPage(state.roomId, beforeCreatedAt = oldest)) {
                is SupabaseResult.Success -> {
                    val merged = (state.messages + page.value).distinctBy { it.id }
                    _ui.update {
                        it.copy(
                            messages = merged,
                            isLoadingHistory = false,
                            hasMoreHistory = page.value.isNotEmpty(),
                        )
                    }
                }
                is SupabaseResult.Failure -> {
                    _ui.update { it.copy(isLoadingHistory = false, error = page.error.message) }
                }
            }
        }
    }

    private fun loadInitial() { /* same as loadOlder with beforeCreatedAt = null */ }

    override fun onCleared() {
        viewModelScope.launch { repo.closeRoom() }
    }
}
```

## 8) Compose screen (lazy list + pagination)

```kotlin
@Composable
fun ChatRoute(vm: ChatViewModel, myUserId: String) {
    val state by vm.ui.collectAsStateWithLifecycle()

    Column {
        LazyColumn(
            reverseLayout = true,
            modifier = Modifier.weight(1f),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(
                    text = msg.body,
                    isMine = msg.senderId == myUserId,
                )
            }

            item {
                LaunchedEffect(state.messages.size) {
                    vm.loadOlder()
                }
                if (state.isLoadingHistory) {
                    CircularProgressIndicator()
                }
            }
        }

        Row {
            TextField(
                value = state.composerText,
                onValueChange = vm::onComposerChanged,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.send(myUserId) }, enabled = !state.sending) {
                Text("Send")
            }
        }
    }
}
```

## 9) Typing + presence

- Use `presence` channel state for online users.
- Use `broadcast` for typing events with short TTL.

```kotlin
subscription.track(buildJsonObject {
    put("userId", myUserId)
    put("typing", false)
})

subscription.broadcast("typing", buildJsonObject {
    put("userId", myUserId)
    put("typing", true)
})
```

## 10) Production recommendations

- Keep message writes idempotent with client message UUID.
- Debounce typing broadcasts (200-500ms).
- Batch read receipts instead of per-message writes.
- Paginate with keyset cursor, not offset, for large rooms.
- Keep strict RLS policies for all chat tables.
