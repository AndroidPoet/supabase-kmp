package io.github.androidpoet.supabase.auth.di
import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.AuthClientImpl
import io.github.androidpoet.supabase.auth.session.SessionConfig
import io.github.androidpoet.supabase.auth.session.SessionManager
import io.github.androidpoet.supabase.auth.session.SessionManagerImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
public fun authModule(config: SessionConfig = SessionConfig()): Module = module {
    singleOf(::AuthClientImpl) bind AuthClient::class
    single<SessionManager> {
        SessionManagerImpl(
            authClient = get(),
            supabaseClient = get(),
            storage = config.storage,
            autoRefresh = config.autoRefresh,
            refreshBufferSeconds = config.refreshBufferSeconds,
        )
    }
}
public val authModule: Module = authModule()
