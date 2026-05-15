package io.github.androidpoet.supabase.functions.di
import io.github.androidpoet.supabase.functions.FunctionsClient
import io.github.androidpoet.supabase.functions.FunctionsClientImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
public val functionsModule: Module = module {
    singleOf(::FunctionsClientImpl) bind FunctionsClient::class
}
