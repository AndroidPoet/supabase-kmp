package io.github.androidpoet.supabase.storage.di
import io.github.androidpoet.supabase.storage.StorageClient
import io.github.androidpoet.supabase.storage.StorageClientImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
public val storageModule: Module = module {
    singleOf(::StorageClientImpl) bind StorageClient::class
}
