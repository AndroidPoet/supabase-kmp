package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.models.Session

/** Default in-memory storage. Sessions don't survive app restarts. */
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
