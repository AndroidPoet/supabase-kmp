package io.github.androidpoet.supabase.sync.paging

import app.cash.paging.ExperimentalPagingApi
import app.cash.paging.LoadType
import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import app.cash.paging.RemoteMediator
import app.cash.paging.RemoteMediatorMediatorResult
import app.cash.paging.RemoteMediatorMediatorResultError
import app.cash.paging.RemoteMediatorMediatorResultSuccess
import io.github.androidpoet.supabase.sync.ChangeKind
import io.github.androidpoet.supabase.sync.LocalStore
import io.github.androidpoet.supabase.sync.PendingChange
import io.github.androidpoet.supabase.sync.Record
import io.github.androidpoet.supabase.sync.SyncEngine
import io.github.androidpoet.supabase.sync.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException

/** Where the store is in its current sync cycle — bind it to a spinner / error banner. */
public enum class SyncStatus { IDLE, SYNCING, ERROR }

/**
 * The one always-in-sync model an app touches per table: **OpenAPI model + SQLDelight + Flow**.
 * A value of type [T] is the same on screen, in the local database, and on the wire. The UI binds
 * [paged] (a list) or [observe] (one row) and calls [upsert] / [delete] — it never touches the
 * network or SQL. Reads reflect a local write immediately (optimistic) and the change is pushed in
 * the background through the [SyncEngine] outbox.
 *
 * ```
 * val notes = SyncStore("notes", local, engine, Note.serializer(), idOf = { it.id }, scope, now)
 * val list: Flow<PagingData<Note>> = notes.paged(20)   // local-first, auto-invalidating
 * val one:  Flow<Note?>            = notes.observe(id)
 * notes.upsert(Note(id, "buy milk"))
 * notes.delete(id)
 * ```
 *
 * @param now wall-clock epoch-millis source (e.g. `{ Clock.System.now().toEpochMilliseconds() }`),
 *   stamped on optimistic writes so last-write-wins resolves correctly across devices.
 */
public class SyncStore<T : Any>(
    private val table: String,
    private val local: LocalStore,
    private val engine: SyncEngine,
    private val serializer: KSerializer<T>,
    private val idOf: (T) -> String,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val json: Json = DefaultJson,
) {
    private val _status = MutableStateFlow(SyncStatus.IDLE)

    /** The latest sync state — `IDLE` / `SYNCING` / `ERROR`. */
    public val status: StateFlow<SyncStatus> = _status.asStateFlow()

    // Bumped on every local write and every completed sync; reads ([observe]) and pagers
    // ([paged]) re-read off it, so what's on screen always matches the local store.
    private val revision = MutableStateFlow(0)

    // Single-flight: one sync runs at a time ([syncMutex]), and a burst of writes collapses to
    // at most one follow-up ([syncTrigger] is CONFLATED) — so rapid upserts don't fan out into
    // overlapping pull/push cycles that double-push the outbox.
    private val syncMutex = Mutex()
    private val syncTrigger = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in syncTrigger) {
                runCatching { sync() }
            }
        }
    }

    /** The row [id], or `null` if absent or tombstoned. One-shot read of the local store. */
    public suspend fun get(id: String): T? =
        local.get(table, id)?.takeUnless { it.deleted }?.let(::decode)

    /** Reactive single row: re-emits whenever the row changes locally or after a sync. */
    public fun observe(id: String): Flow<T?> =
        revision.map { get(id) }.distinctUntilChanged()

    /** Optimistically insert/replace [value]: visible to reads at once, queued to push next sync. */
    public suspend fun upsert(value: T) {
        val record = Record(idOf(value), now(), deleted = false, fields = encode(value))
        // Local writes go through enqueue (the optimistic path), NOT local.upsert: upsert carries the
        // remote monotonic guard, which would silently drop this edit whenever the client clock is
        // behind the row's stored (server) updatedAt. enqueue applies the row and queues it atomically.
        local.enqueue(table, PendingChange(record, ChangeKind.UPSERT))
        bump()
        syncTrigger.trySend(Unit)
    }

    /** Optimistically delete [id] via a soft-delete tombstone, queued to push next sync. */
    public suspend fun delete(id: String) {
        val existing = local.get(table, id)
        val record = Record(id, now(), deleted = true, fields = existing?.fields ?: EMPTY_FIELDS)
        // Route through enqueue (not local.upsert), same as [upsert]: enqueue writes the tombstone and
        // queues it atomically, unguarded — local.upsert's remote monotonic guard could otherwise drop
        // the delete when the client clock trails the row's stored updatedAt.
        local.enqueue(table, PendingChange(record, ChangeKind.DELETE))
        bump()
        syncTrigger.trySend(Unit)
    }

    /**
     * Runs one pull → merge → push cycle, updating [status]. Reads refresh on completion.
     * Serialized by [syncMutex] so a manual call and the background trigger never overlap.
     */
    public suspend fun sync(): SyncResult =
        syncMutex.withLock {
            _status.value = SyncStatus.SYNCING
            try {
                engine.sync(table).also {
                    _status.value = SyncStatus.IDLE
                    bump()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _status.value = SyncStatus.ERROR
                throw e
            }
        }

    /**
     * A Paging 3 [PagingData] stream over the **local** store — loads one [pageSize] window at a
     * time (never the whole table) and invalidates itself on any local write or completed sync.
     * Drops straight into a Compose `LazyColumn` via `collectAsLazyPagingItems()`.
     */
    public fun paged(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<T>> =
        Pager(
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { LocalPagingSource(table, local, ::decode, revision, scope) },
        ).flow

    /**
     * Like [paged], but for a large server table you don't want to download up front: a Paging 3
     * `RemoteMediator` pulls the **next backend page on demand** as the user scrolls (via the
     * engine's cursor-advancing [SyncEngine.pullPage]) and writes it to the local store, so it's
     * then available offline. Local writes still push through [upsert] / [delete] as usual.
     */
    @OptIn(ExperimentalPagingApi::class)
    public fun pagedSynced(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<T>> =
        Pager(
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            remoteMediator = SyncRemoteMediator(table, engine, ::bump),
            pagingSourceFactory = { LocalPagingSource(table, local, ::decode, revision, scope) },
        ).flow

    private fun bump() {
        revision.update { it + 1 }
    }

    private fun decode(record: Record): T = json.decodeFromJsonElement(serializer, record.fields)

    // `.jsonObject` throws a clear IllegalArgumentException if T doesn't serialize to a JSON object
    // (a row model always does); friendlier than a raw ClassCastException.
    private fun encode(value: T): JsonObject = json.encodeToJsonElement(serializer, value).jsonObject

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        val EMPTY_FIELDS = JsonObject(emptyMap())
    }
}

/** Lenient, default-encoding [Json] used when no custom instance is supplied. */
public val DefaultJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * Pages the local store by **keyset** (the last row id), not absolute offset, so inserts from a
 * background sync can't duplicate or skip rows across pages. The key is the id to read after
 * (`null` = first page). Terminates as soon as a page comes back short, so a fully-loaded list
 * never re-loads forever.
 */
private class LocalPagingSource<T : Any>(
    private val table: String,
    private val local: LocalStore,
    private val decode: (Record) -> T,
    revision: StateFlow<Int>,
    scope: CoroutineScope,
) : PagingSource<String, T>() {
    init {
        // A PagingSource is single-use: invalidating it makes the Pager build a fresh one (which
        // re-subscribes here). So we await exactly ONE revision bump, then invalidate — the job
        // self-completes (no leak) and reads no shared mutable state (no race). If the source is
        // invalidated for any other reason first, the callback cancels the still-waiting job.
        val job =
            scope.launch {
                revision.drop(1).first()
                invalidate()
            }
        registerInvalidatedCallback { job.cancel() }
    }

    override suspend fun load(params: PagingSourceLoadParams<String>): PagingSourceLoadResult<String, T> {
        val limit = params.loadSize
        // Keep the records (for their ids → next keyset key) alongside the decoded values.
        val records = local.pageAfter(table, params.key, limit.toLong())
        @Suppress("UNCHECKED_CAST")
        return PagingSourceLoadResultPage(
            data = records.map(decode),
            prevKey = null, // forward-only keyset; PREPEND isn't supported (and isn't needed for a feed)
            // Short page → no nextKey → paging stops. This is what keeps it from looping.
            nextKey = if (records.size < limit) null else records.last().id,
        ) as PagingSourceLoadResult<String, T>
    }

    // A keyset can't address "the row before the anchor", so reload from the head on refresh. The
    // head rows are stable, so Compose keeps scroll position by item key.
    override fun getRefreshKey(state: PagingState<String, T>): String? = null
}

/**
 * Pulls the next backend page into the local store as the user scrolls (APPEND), writing each page
 * locally so it's then available offline. The cursor is forward-only, so PREPEND is always the end
 * and REFRESH never moves it — that (not an initial-refresh hack) is what stops the mediator from
 * draining the whole table in a loop the moment it's subscribed.
 */
@OptIn(ExperimentalPagingApi::class)
private class SyncRemoteMediator<T : Any>(
    private val table: String,
    private val engine: SyncEngine,
    private val onLoaded: () -> Unit,
) : RemoteMediator<String, T>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, T>,
    ): RemoteMediatorMediatorResult =
        try {
            val endOfPagination =
                when (loadType) {
                    LoadType.PREPEND -> true // forward-only cursor: nothing precedes the head
                    LoadType.REFRESH -> false // show whatever is local; don't advance the cursor
                    LoadType.APPEND -> {
                        val progress = engine.pullPage(table)
                        onLoaded()
                        !progress.hasMore
                    }
                }
            RemoteMediatorMediatorResultSuccess(endOfPaginationReached = endOfPagination)
                as RemoteMediatorMediatorResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            RemoteMediatorMediatorResultError(e) as RemoteMediatorMediatorResult
        }
}
