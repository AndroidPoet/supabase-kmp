package io.github.androidpoet.supabase.auth.admin

import io.github.androidpoet.supabase.client.SupabaseClient

/**
 * Creates an [AuthAdminClient] for the GoTrue **admin** API, authenticated with the
 * project's [serviceRoleKey].
 *
 * **SECURITY — server-side only.** The service-role key bypasses Row Level Security
 * and can read, modify or delete *any* user and *any* row in the project. It must
 * **never** ship in a client app (mobile / web / desktop); keep it in a trusted
 * backend or edge function. The key is a required argument with no default
 * precisely so an admin client can't be created by accident with the public anon
 * key — pass the real service-role key explicitly and source it from a secret, not
 * from committed code.
 */
public fun createAuthAdminClient(
    supabaseClient: SupabaseClient,
    serviceRoleKey: String,
): AuthAdminClient = AuthAdminClientImpl(client = supabaseClient, serviceRoleKey = serviceRoleKey)
