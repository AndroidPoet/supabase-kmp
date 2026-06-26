package io.github.androidpoet.supabase.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Orchestrates one offline-first sync cycle per table: pull remote changes since the last
 * cursor, merge them against the local outbox (resolving conflicts), then push local changes.
 *
 * The engine is transport-agnostic — it talks only to [LocalStore] and [RemoteSource], so the
 * same logic drives Supabase today and anything else behind those interfaces tomorrow.
 */
public class SyncEngine(
    private val local: LocalStore,
    private val remote: RemoteSource,
    private val resolvers: ResolverRegistry = ResolverRegistry(),
) {
    /**
     * Runs a full pull → merge → push cycle for [table] and returns what moved.
     *
     * Conflicts are resolved per row by the table's [ConflictResolver]. If the remote version
     * wins, the local edit is dropped from the outbox; otherwise the *winner* (the local edit,
     * or a custom merge) is re-queued so the resolved value — not the now-stale original — is
     * what gets pushed.
     */
    public suspend fun sync(table: String): SyncResult {
        // Drain every pending remote page before pushing — `pullPage` advances the cursor, so this
        // is a full catch-up, not just one page. Bounded by [MAX_PULL_PAGES] so a misbehaving
        // server that never lowers `hasMore` can't spin forever.
        var pulled = 0
        var page = pullPage(table)
        pulled += page.pulled
        var guard = 0
        while (page.hasMore && guard++ < MAX_PULL_PAGES) {
            page = pullPage(table)
            pulled += page.pulled
        }

        val pending = local.pending(table)
        val push = if (pending.isEmpty()) PushResult(emptyList()) else remote.push(table, pending)
        if (push.accepted.isNotEmpty()) local.clearPending(table, push.accepted)

        return SyncResult(
            pulled = pulled,
            pushed = push.accepted.size,
            rejected = push.rejected.size,
        )
    }

    /**
     * Pulls and merges a single page of remote changes (no push), advancing the table's cursor.
     * Call it repeatedly to walk a large table page-by-page — each call resolves conflicts the
     * same way [sync] does. Powers `SyncStore.pagedSynced` (a Paging 3 `RemoteMediator`).
     *
     * @return how many rows the page carried and whether another page likely follows.
     */
    public suspend fun pullPage(table: String): PullProgress {
        val since = local.cursor(table)
        val pull = remote.pull(table, since)

        val pendingById = local.pending(table).associateBy { it.record.id }
        val resolver = resolvers.forTable(table)

        val toStore = ArrayList<Record>(pull.changed.size)
        val clearedConflicts = mutableListOf<String>()
        val reEnqueue = mutableListOf<PendingChange>()

        for (incoming in pull.changed) {
            val localPending = pendingById[incoming.id]?.record
            if (localPending == null) {
                toStore += incoming
                continue
            }
            val winner = resolver.resolve(localPending, incoming)
            toStore += winner
            // Reconcile the outbox with the resolved winner. The remote winning means the local
            // edit lost and must be dropped; anything else (the local edit, or a merge) must be
            // pushed, so re-queue the winner rather than the stale original.
            clearedConflicts += incoming.id
            if (winner != incoming) {
                reEnqueue += PendingChange(winner, if (winner.deleted) ChangeKind.DELETE else ChangeKind.UPSERT)
            }
        }

        local.upsert(table, toStore)
        if (clearedConflicts.isNotEmpty()) local.clearPending(table, clearedConflicts)
        for (change in reEnqueue) local.enqueue(table, change)
        // A null cursor means "don't advance" — resetting it to null would force a full re-sync.
        pull.nextCursor?.let { local.setCursor(table, it) }

        return PullProgress(
            pulled = pull.changed.size,
            // More to come only if the server both moved the cursor AND returned rows; an empty
            // page (or a held cursor) means we've drained the table — stops the mediator.
            hasMore = pull.nextCursor != null && pull.changed.isNotEmpty(),
        )
    }

    /**
     * A live stream of [table]'s remote changes, each merged into the local store as it arrives.
     * Collect this alongside periodic [sync] calls for realtime updates; still re-run [sync] on
     * reconnect to backfill deltas missed while disconnected.
     *
     * Each incoming change is run through the same [ConflictResolver] as [sync], so a live remote
     * echo can't clobber a newer local edit that is still waiting in the outbox.
     */
    private companion object {
        // A full sync drains at most this many remote pages — a backstop against a server that
        // keeps reporting `hasMore` without advancing. At the default 1000 rows/page that's 10M rows.
        const val MAX_PULL_PAGES = 10_000
    }

    public fun observe(table: String): Flow<RemoteChange> =
        remote.changes(table).onEach { change ->
            val localPending = local.pending(table).firstOrNull { it.record.id == change.record.id }?.record
            val winner =
                if (localPending == null) {
                    change.record
                } else {
                    resolvers.forTable(table).resolve(localPending, change.record)
                }
            local.upsert(table, listOf(winner))
        }
}
