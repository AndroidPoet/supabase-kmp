package io.github.androidpoet.supabase.auth.session
import io.github.androidpoet.supabase.auth.models.Session
import kotlin.concurrent.Volatile

/**
 * A [SessionStorage] that keeps the session only in memory. **It is lost when the
 * process is killed**, signing the user out on the next launch. This is the
 * default in [SessionConfig] for convenience, but production apps should supply a
 * durable storage (e.g. a [KeyValueSessionStorage] backed by your platform's
 * persistence) so sign-in survives restarts.
 */
public class InMemorySessionStorage : SessionStorage {
    // save()/load() may run on different threads of the default dispatcher; @Volatile
    // gives a save() on one thread visibility from a load() on another. Each access is
    // a single read or write (no compound update), so visibility is all that's needed.
    @Volatile
    private var stored: Session? = null

    override suspend fun save(session: Session) {
        stored = session
    }

    override suspend fun load(): Session? = stored

    override suspend fun clear() {
        stored = null
    }
}
