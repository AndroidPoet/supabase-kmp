package io.github.androidpoet.supabase.database
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import kotlinx.serialization.encodeToString
public suspend inline fun <reified T> DatabaseClient.selectTyped(
    table: String,
    columns: String = "*",
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<List<T>> =
    select(table = table, columns = columns, filters = filters).deserialize()
public suspend inline fun <reified T> DatabaseClient.insertTyped(
    table: String,
    value: T,
    upsert: Boolean = false,
    onConflict: String? = null,
): SupabaseResult<List<T>> =
    insert(
        table = table,
        body = defaultJson.encodeToString(value),
        upsert = upsert,
        onConflict = onConflict,
    ).deserialize()
public suspend inline fun <reified T> DatabaseClient.updateTyped(
    table: String,
    value: T,
    noinline filters: FilterBuilder.() -> Unit = {},
): SupabaseResult<List<T>> =
    update(
        table = table,
        body = defaultJson.encodeToString(value),
        filters = filters,
    ).deserialize()
public suspend inline fun <reified T> DatabaseClient.rpcTyped(
    function: String,
    params: String? = null,
): SupabaseResult<T> =
    rpc(function = function, params = params).deserialize()
