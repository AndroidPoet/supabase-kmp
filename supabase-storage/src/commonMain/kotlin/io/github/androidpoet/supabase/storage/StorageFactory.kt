package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.client.SupabaseClient

public fun createStorageClient(supabaseClient: SupabaseClient): StorageClient =
    StorageClientImpl(client = supabaseClient)
