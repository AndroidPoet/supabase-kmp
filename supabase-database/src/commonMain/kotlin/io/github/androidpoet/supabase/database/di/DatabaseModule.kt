package io.github.androidpoet.supabase.database.di
import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.database.DatabaseClientImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
public val databaseModule: Module = module {
    singleOf(::DatabaseClientImpl) bind DatabaseClient::class
}
