package io.github.androidpoet.supabase.sync

/**
 * Creates a [SyncEngine] over a [local] store and a [remote] source — the convention-matching
 * entry point that mirrors the SDK's other `create*` factories. Equivalent to calling the
 * [SyncEngine] constructor directly; prefer this for consistency with the rest of `supabase-kmp`.
 *
 * @param resolvers per-table [ConflictResolver]s; defaults to last-write-wins for every table.
 */
public fun createSyncEngine(
    local: LocalStore,
    remote: RemoteSource,
    resolvers: ResolverRegistry = ResolverRegistry(),
): SyncEngine = SyncEngine(local = local, remote = remote, resolvers = resolvers)
