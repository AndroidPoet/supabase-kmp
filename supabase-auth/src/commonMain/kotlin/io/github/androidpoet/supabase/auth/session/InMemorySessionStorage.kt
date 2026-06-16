package io.github.androidpoet.supabase.auth.session
import io.github.androidpoet.supabase.auth.models.Session

/**
 * A [SessionStorage] that keeps the session only in memory. **It is lost when the
 * process is killed**, signing the user out on the next launch. This is the
 * default in [SessionConfig] for convenience, but production apps should supply a
 * durable storage (e.g. a [KeyValueSessionStorage] backed by your platform's
 * persistence) so sign-in survives restarts.
 */
public class InMemorySessionStorage : SessionStorage {
    private var stored: Session? = null

    override suspend fun save(session: Session) {
        stored = session
    }

    override suspend fun load(): Session? = stored

    override suspend fun clear() {
        stored = null
    }
}
