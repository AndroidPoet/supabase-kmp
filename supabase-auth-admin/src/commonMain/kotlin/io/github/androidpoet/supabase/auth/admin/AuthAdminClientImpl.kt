package io.github.androidpoet.supabase.auth.admin

import io.github.androidpoet.supabase.auth.admin.models.AdminUserAttributes
import io.github.androidpoet.supabase.auth.admin.models.CustomProvider
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderCreateRequest
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderListResponse
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderType
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderUpdateRequest
import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkRequest
import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkResponse
import io.github.androidpoet.supabase.auth.admin.models.InviteUserRequest
import io.github.androidpoet.supabase.auth.admin.models.ListUsersResponse
import io.github.androidpoet.supabase.auth.admin.models.MfaAdminDeleteFactorResponse
import io.github.androidpoet.supabase.auth.admin.models.MfaAdminListFactorsResponse
import io.github.androidpoet.supabase.auth.admin.models.OAuthClient
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientCreateRequest
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientListResponse
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientUpdateRequest
import io.github.androidpoet.supabase.auth.admin.models.Passkey
import io.github.androidpoet.supabase.auth.admin.models.UserDeleteRequest
import io.github.androidpoet.supabase.auth.admin.models.UserResponse
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.core.util.urlEncode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

internal class AuthAdminClientImpl(
    private val client: SupabaseClient,
    serviceRoleKey: String,
) : AuthAdminClient {
    private val adminHeaders: Map<String, String> = mapOf("Authorization" to "Bearer $serviceRoleKey")

    override suspend fun signOut(
        jwt: String,
        scope: SignOutScope,
    ): SupabaseResult<Unit> =
        client
            .post(
                endpoint = "${AuthAdminPaths.LOGOUT}?scope=${scope.name.lowercase()}",
                headers = mapOf("Authorization" to "Bearer $jwt"),
            ).map { }

    override suspend fun inviteUserByEmail(
        email: String,
        data: JsonObject?,
        redirectTo: String?,
    ): SupabaseResult<User> {
        val body = defaultJson.encodeToString(InviteUserRequest(email = email, data = data))
        return client
            .post(
                endpoint = withRedirectTo(AuthAdminPaths.INVITE, redirectTo),
                body = body,
                headers = adminHeaders,
            ).deserializeUserResponse()
    }

    override suspend fun generateLink(request: GenerateLinkRequest): SupabaseResult<GenerateLinkResponse> {
        val body = defaultJson.encodeToString(request)
        return client
            .post(
                endpoint = withRedirectTo(AuthAdminPaths.ADMIN_GENERATE_LINK, request.redirectTo),
                body = body,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun createUser(attributes: AdminUserAttributes): SupabaseResult<User> {
        val body = defaultJson.encodeToString(attributes)
        return client
            .post(
                endpoint = AuthAdminPaths.ADMIN_USERS,
                body = body,
                headers = adminHeaders,
            ).deserializeUserResponse()
    }

    override suspend fun listUsers(
        page: Int?,
        perPage: Int?,
    ): SupabaseResult<ListUsersResponse> {
        val queryParams =
            buildList {
                if (page != null) add("page" to page.toString())
                if (perPage != null) add("per_page" to perPage.toString())
            }
        return client
            .get(
                endpoint = AuthAdminPaths.ADMIN_USERS,
                queryParams = queryParams,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun getUserById(userId: String): SupabaseResult<User> =
        client
            .get(
                endpoint = "${AuthAdminPaths.ADMIN_USERS}/$userId",
                headers = adminHeaders,
            ).deserializeUserResponse()

    override suspend fun updateUserById(
        userId: String,
        attributes: AdminUserAttributes,
    ): SupabaseResult<User> {
        val body = defaultJson.encodeToString(attributes)
        return client
            .put(
                endpoint = "${AuthAdminPaths.ADMIN_USERS}/$userId",
                body = body,
                headers = adminHeaders,
            ).deserializeUserResponse()
    }

    override suspend fun deleteUser(
        userId: String,
        shouldSoftDelete: Boolean,
    ): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(UserDeleteRequest(shouldSoftDelete = shouldSoftDelete))
        return client
            .delete(
                endpoint = "${AuthAdminPaths.ADMIN_USERS}/$userId",
                body = body,
                headers = adminHeaders,
            ).map { }
    }

    override suspend fun listFactors(userId: String): SupabaseResult<MfaAdminListFactorsResponse> =
        client
            .get(
                endpoint = "${AuthAdminPaths.ADMIN_USERS}/$userId/factors",
                headers = adminHeaders,
            ).deserialize()

    override suspend fun deleteFactor(
        userId: String,
        factorId: String,
    ): SupabaseResult<MfaAdminDeleteFactorResponse> =
        client
            .delete(
                endpoint = "${AuthAdminPaths.ADMIN_USERS}/$userId/factors/$factorId",
                headers = adminHeaders,
            ).deserialize()

    override suspend fun listOAuthClients(
        page: Int?,
        perPage: Int?,
    ): SupabaseResult<OAuthClientListResponse> {
        val queryParams =
            buildList {
                if (page != null) add("page" to page.toString())
                if (perPage != null) add("per_page" to perPage.toString())
            }
        return client
            .get(
                endpoint = AuthAdminPaths.ADMIN_OAUTH_CLIENTS,
                queryParams = queryParams,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun createOAuthClient(request: OAuthClientCreateRequest): SupabaseResult<OAuthClient> {
        val body = defaultJson.encodeToString(request)
        return client
            .post(
                endpoint = AuthAdminPaths.ADMIN_OAUTH_CLIENTS,
                body = body,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun getOAuthClient(clientId: String): SupabaseResult<OAuthClient> =
        client
            .get(
                endpoint = "${AuthAdminPaths.ADMIN_OAUTH_CLIENTS}/$clientId",
                headers = adminHeaders,
            ).deserialize()

    override suspend fun updateOAuthClient(
        clientId: String,
        request: OAuthClientUpdateRequest,
    ): SupabaseResult<OAuthClient> {
        val body = defaultJson.encodeToString(request)
        return client
            .put(
                endpoint = "${AuthAdminPaths.ADMIN_OAUTH_CLIENTS}/$clientId",
                body = body,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun deleteOAuthClient(clientId: String): SupabaseResult<Unit> =
        client
            .delete(
                endpoint = "${AuthAdminPaths.ADMIN_OAUTH_CLIENTS}/$clientId",
                headers = adminHeaders,
            ).map { }

    override suspend fun regenerateOAuthClientSecret(clientId: String): SupabaseResult<OAuthClient> =
        client
            .post(
                endpoint = "${AuthAdminPaths.ADMIN_OAUTH_CLIENTS}/$clientId/regenerate_secret",
                headers = adminHeaders,
            ).deserialize()

    override suspend fun listCustomProviders(type: CustomProviderType?): SupabaseResult<CustomProviderListResponse> {
        val queryParams =
            buildList {
                if (type != null) add("type" to type.value)
            }
        return client
            .get(
                endpoint = AuthAdminPaths.ADMIN_CUSTOM_PROVIDERS,
                queryParams = queryParams,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun createCustomProvider(request: CustomProviderCreateRequest): SupabaseResult<CustomProvider> {
        val body = defaultJson.encodeToString(request)
        return client
            .post(
                endpoint = AuthAdminPaths.ADMIN_CUSTOM_PROVIDERS,
                body = body,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun getCustomProvider(identifier: String): SupabaseResult<CustomProvider> =
        client
            .get(
                endpoint = "${AuthAdminPaths.ADMIN_CUSTOM_PROVIDERS}/$identifier",
                headers = adminHeaders,
            ).deserialize()

    override suspend fun updateCustomProvider(
        identifier: String,
        request: CustomProviderUpdateRequest,
    ): SupabaseResult<CustomProvider> {
        val body = defaultJson.encodeToString(request)
        return client
            .put(
                endpoint = "${AuthAdminPaths.ADMIN_CUSTOM_PROVIDERS}/$identifier",
                body = body,
                headers = adminHeaders,
            ).deserialize()
    }

    override suspend fun deleteCustomProvider(identifier: String): SupabaseResult<Unit> =
        client
            .delete(
                endpoint = "${AuthAdminPaths.ADMIN_CUSTOM_PROVIDERS}/$identifier",
                headers = adminHeaders,
            ).map { }

    override suspend fun listPasskeys(userId: String): SupabaseResult<List<Passkey>> =
        client
            .get(
                endpoint = "${AuthAdminPaths.ADMIN_USERS}/$userId/passkeys",
                headers = adminHeaders,
            ).deserialize()

    override suspend fun deletePasskey(
        userId: String,
        passkeyId: String,
    ): SupabaseResult<Unit> =
        client
            .delete(
                endpoint = "${AuthAdminPaths.ADMIN_USERS}/$userId/passkeys/$passkeyId",
                headers = adminHeaders,
            ).map { }

    private fun SupabaseResult<String>.deserializeUserResponse(): SupabaseResult<User> =
        when (val result = deserialize<UserResponse>()) {
            is SupabaseResult.Failure -> result
            is SupabaseResult.Success -> {
                val user = result.value.user
                if (user == null) {
                    SupabaseResult.Failure(SupabaseError("Auth admin response did not include a user"))
                } else {
                    SupabaseResult.Success(user)
                }
            }
        }

    private fun withRedirectTo(endpoint: String, redirectTo: String?): String =
        if (redirectTo == null) {
            endpoint
        } else {
            "$endpoint?redirect_to=${urlEncode(redirectTo)}"
        }
}
