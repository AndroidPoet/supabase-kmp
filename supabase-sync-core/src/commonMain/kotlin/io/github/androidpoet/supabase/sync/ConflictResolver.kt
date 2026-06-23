package io.github.androidpoet.supabase.sync

/**
 * Decides the winner when a local pending change and an incoming remote record touch the same
 * row. The default is [LastWriteWins]; register a custom resolver per table on a
 * [ResolverRegistry] to do field-level merges or any other policy your app needs.
 */
public fun interface ConflictResolver {
    public fun resolve(local: Record, remote: Record): Record
}

/** Higher [Record.updatedAt] wins; ties go to the remote, since the server is authoritative. */
public object LastWriteWins : ConflictResolver {
    override fun resolve(local: Record, remote: Record): Record =
        if (local.updatedAt > remote.updatedAt) local else remote
}

/** Maps tables to their [ConflictResolver], falling back to [default] for unregistered tables. */
public class ResolverRegistry(
    private val default: ConflictResolver = LastWriteWins,
) {
    private val perTable = mutableMapOf<String, ConflictResolver>()

    /** Registers [resolver] for [table]; returns `this` for chaining. */
    public fun register(table: String, resolver: ConflictResolver): ResolverRegistry {
        perTable[table] = resolver
        return this
    }

    /** The resolver for [table], or [default] if none was registered. */
    public fun forTable(table: String): ConflictResolver = perTable[table] ?: default
}
