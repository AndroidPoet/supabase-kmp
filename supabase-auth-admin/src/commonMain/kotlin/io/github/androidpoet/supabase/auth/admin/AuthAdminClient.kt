package io.github.androidpoet.supabase.auth.admin

import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.admin.models.AdminUserAttributes
import io.github.androidpoet.supabase.auth.admin.models.CustomProvider
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderListResponse
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderType
import io.github.androidpoet.supabase.auth.admin.models.OAuthClient
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientCreateRequest
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientListResponse
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientUpdateRequest
import io.github.androidpoet.supabase.auth.admin.models.Passkey
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderCreateRequest
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderUpdateRequest
import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkRequest
import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkResponse
import io.github.androidpoet.supabase.auth.admin.models.ListUsersResponse
import io.github.androidpoet.supabase.auth.admin.models.MfaAdminDeleteFactorResponse
import io.github.androidpoet.supabase.auth.admin.models.MfaAdminListFactorsResponse
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonObject

public interface AuthAdminClient {
    public suspend fun signOut(
        jwt: String,
        scope: SignOutScope = SignOutScope.LOCAL,
    ): SupabaseResult<Unit>

    public suspend fun inviteUserByEmail(
        email: String,
        data: JsonObject? = null,
        redirectTo: String? = null,
    ): SupabaseResult<User>

    public suspend fun generateLink(request: GenerateLinkRequest): SupabaseResult<GenerateLinkResponse>

    public suspend fun createUser(attributes: AdminUserAttributes): SupabaseResult<User>

    public suspend fun listUsers(
        page: Int? = null,
        perPage: Int? = null,
    ): SupabaseResult<ListUsersResponse>

    public suspend fun getUserById(userId: String): SupabaseResult<User>

    public suspend fun updateUserById(
        userId: String,
        attributes: AdminUserAttributes,
    ): SupabaseResult<User>

    public suspend fun deleteUser(
        userId: String,
        shouldSoftDelete: Boolean = false,
    ): SupabaseResult<Unit>

    public suspend fun listFactors(userId: String): SupabaseResult<MfaAdminListFactorsResponse>

    public suspend fun deleteFactor(
        userId: String,
        factorId: String,
    ): SupabaseResult<MfaAdminDeleteFactorResponse>

    public suspend fun listOAuthClients(
        page: Int? = null,
        perPage: Int? = null,
    ): SupabaseResult<OAuthClientListResponse>

    public suspend fun createOAuthClient(request: OAuthClientCreateRequest): SupabaseResult<OAuthClient>

    public suspend fun getOAuthClient(clientId: String): SupabaseResult<OAuthClient>

    public suspend fun updateOAuthClient(
        clientId: String,
        request: OAuthClientUpdateRequest,
    ): SupabaseResult<OAuthClient>

    public suspend fun deleteOAuthClient(clientId: String): SupabaseResult<Unit>

    public suspend fun regenerateOAuthClientSecret(clientId: String): SupabaseResult<OAuthClient>

    public suspend fun listCustomProviders(type: CustomProviderType? = null): SupabaseResult<CustomProviderListResponse>

    public suspend fun createCustomProvider(request: CustomProviderCreateRequest): SupabaseResult<CustomProvider>

    public suspend fun getCustomProvider(identifier: String): SupabaseResult<CustomProvider>

    public suspend fun updateCustomProvider(
        identifier: String,
        request: CustomProviderUpdateRequest,
    ): SupabaseResult<CustomProvider>

    public suspend fun deleteCustomProvider(identifier: String): SupabaseResult<Unit>

    public suspend fun listPasskeys(userId: String): SupabaseResult<List<Passkey>>

    public suspend fun deletePasskey(
        userId: String,
        passkeyId: String,
    ): SupabaseResult<Unit>
}
