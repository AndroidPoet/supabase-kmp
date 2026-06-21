package io.github.androidpoet.supabase.auth.admin

import io.github.androidpoet.supabase.auth.admin.models.AdminUserAttributes
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderCreateRequest
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderType
import io.github.androidpoet.supabase.auth.admin.models.CustomProviderUpdateRequest
import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkRequest
import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkType
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientCreateRequest
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientTokenEndpointAuthMethod
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientUpdateRequest
import io.github.androidpoet.supabase.auth.admin.models.SsoProviderCreateRequest
import io.github.androidpoet.supabase.auth.admin.models.SsoProviderUpdateRequest
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthAdminClientImplTest {
    private val json: Json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_createUser_sendsAdminUserAttributes_expectedEndpointBodyAndHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result =
                sut.createUser(
                    AdminUserAttributes(
                        email = "user@example.com",
                        password = "secret",
                        userMetadata = JsonObject(mapOf("name" to JsonPrimitive("Ranbir"))),
                        emailConfirm = true,
                    ),
                )

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users", client.lastPostEndpoint)
            assertEquals("Bearer service-role", client.lastPostHeaders["Authorization"])
            val body = json.parseToJsonElement(client.lastPostBody ?: error("missing body")).jsonObject
            assertEquals("user@example.com", body["email"]?.jsonPrimitive?.content)
            assertEquals("secret", body["password"]?.jsonPrimitive?.content)
            assertEquals(true, body["email_confirm"]?.jsonPrimitive?.boolean)
            assertEquals(
                "Ranbir",
                body["user_metadata"]
                    ?.jsonObject
                    ?.get("name")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun test_listUsers_withPaging_decodesUsersAndSendsQueryParams() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.listUsers(page = 2, perPage = 50)

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users", client.lastGetEndpoint)
            assertEquals(listOf("page" to "2", "per_page" to "50"), client.lastGetQueryParams)
            assertEquals("Bearer service-role", client.lastGetHeaders["Authorization"])
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.ListUsersResponse
            assertEquals("authenticated", value.aud)
            assertEquals("u1", value.users.first().id)
        }

    @Test
    fun test_getUserById_decodesUserResponse() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.getUserById("u1")

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1", client.lastGetEndpoint)
            assertEquals("u1", (success.value as io.github.androidpoet.supabase.auth.models.User).id)
        }

    @Test
    fun test_updateUserById_sendsPutBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.updateUserById("u1", AdminUserAttributes(phone = "+15555550100", phoneConfirm = true))

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1", client.lastPutEndpoint)
            assertEquals("Bearer service-role", client.lastPutHeaders["Authorization"])
            val body = json.parseToJsonElement(client.lastPutBody ?: error("missing body")).jsonObject
            assertEquals("+15555550100", body["phone"]?.jsonPrimitive?.content)
            assertEquals(true, body["phone_confirm"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun test_deleteUser_sendsAdminDelete() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.deleteUser(userId = "u1", shouldSoftDelete = true)

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1", client.lastDeleteEndpoint)
            assertEquals("Bearer service-role", client.lastDeleteHeaders["Authorization"])
            val body = json.parseToJsonElement(client.lastDeleteBody ?: error("missing body")).jsonObject
            assertEquals(true, body["should_soft_delete"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun test_listFactors_decodesAdminMfaFactors() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.listFactors("u1")

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1/factors", client.lastGetEndpoint)
            assertEquals("Bearer service-role", client.lastGetHeaders["Authorization"])
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.MfaAdminListFactorsResponse
            assertEquals("factor1", value.factors.first().id)
            assertEquals(io.github.androidpoet.supabase.auth.models.MfaFactorType.WEBAUTHN, value.factors.last().factorType)
        }

    @Test
    fun test_deleteFactor_sendsAdminMfaDeleteAndDecodesId() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.deleteFactor(userId = "u1", factorId = "factor1")

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1/factors/factor1", client.lastDeleteEndpoint)
            assertEquals("Bearer service-role", client.lastDeleteHeaders["Authorization"])
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.MfaAdminDeleteFactorResponse
            assertEquals("factor1", value.id)
        }

    @Test
    fun test_updateFactor_sendsPutWithFriendlyNameAndDecodesFactor() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.updateFactor(userId = "u1", factorId = "factor1", friendlyName = "Renamed Key")

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1/factors/factor1", client.lastPutEndpoint)
            assertEquals("Bearer service-role", client.lastPutHeaders["Authorization"])
            val body = json.parseToJsonElement(client.lastPutBody ?: error("missing body")).jsonObject
            assertEquals("Renamed Key", body["friendly_name"]?.jsonPrimitive?.content)
            val value = success.value as io.github.androidpoet.supabase.auth.models.MfaFactor
            assertEquals("factor1", value.id)
            assertEquals("Renamed Key", value.friendlyName)
        }

    @Test
    fun test_auditLogEvents_parsesBareArrayAndSendsQueryParams() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.auditLogEvents(page = 2, perPage = 50)

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/audit", client.lastGetEndpoint)
            assertEquals(listOf("page" to "2", "per_page" to "50"), client.lastGetQueryParams)
            assertEquals("Bearer service-role", client.lastGetHeaders["Authorization"])
            val value = success.value as List<*>
            val entry = value.first() as io.github.androidpoet.supabase.auth.admin.models.AuditLogEntry
            assertEquals("audit1", entry.id)
            assertEquals("203.0.113.7", entry.ipAddress)
            assertEquals(
                "login",
                entry.payload
                    ?.get("action")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun test_listPasskeys_decodesAdminPasskeyArray() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.listPasskeys("u1")

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1/passkeys", client.lastGetEndpoint)
            assertEquals("Bearer service-role", client.lastGetHeaders["Authorization"])
            val value = success.value as List<*>
            val passkey = value.first() as io.github.androidpoet.supabase.auth.admin.models.Passkey
            assertEquals("passkey1", passkey.id)
            assertEquals("Laptop", passkey.friendlyName)
        }

    @Test
    fun test_deletePasskey_sendsAdminPasskeyDelete() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.deletePasskey(userId = "u1", passkeyId = "passkey1")

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/users/u1/passkeys/passkey1", client.lastDeleteEndpoint)
            assertEquals("Bearer service-role", client.lastDeleteHeaders["Authorization"])
        }

    @Test
    fun test_inviteUserByEmail_sendsRedirectQueryAndData() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result =
                sut.inviteUserByEmail(
                    email = "invite@example.com",
                    data = JsonObject(mapOf("team" to JsonPrimitive("sdk"))),
                    redirectTo = "app://callback",
                )

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/invite?redirect_to=app%3A%2F%2Fcallback", client.lastPostEndpoint)
            val body = json.parseToJsonElement(client.lastPostBody ?: error("missing body")).jsonObject
            assertEquals("invite@example.com", body["email"]?.jsonPrimitive?.content)
            assertEquals(
                "sdk",
                body["data"]
                    ?.jsonObject
                    ?.get("team")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun test_generateLink_mapsRequestAndDecodesProperties() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result =
                sut.generateLink(
                    GenerateLinkRequest(
                        type = GenerateLinkType.EMAIL_CHANGE_NEW,
                        email = "old@example.com",
                        newEmail = "new@example.com",
                        redirectTo = "app://callback",
                    ),
                )

            val success = assertIs<SupabaseResult.Success<*>>(result)
            // redirect_to travels in the body for this endpoint, not as a query param.
            assertEquals("/auth/v1/admin/generate_link", client.lastPostEndpoint)
            val body = json.parseToJsonElement(client.lastPostBody ?: error("missing body")).jsonObject
            assertEquals("email_change_new", body["type"]?.jsonPrimitive?.content)
            assertEquals("new@example.com", body["new_email"]?.jsonPrimitive?.content)
            assertEquals("app://callback", body["redirect_to"]?.jsonPrimitive?.content)
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.GenerateLinkResponse
            // The flat GoTrue response is reshaped: the five link fields populate `properties`...
            assertEquals("https://example.com/action", value.properties.actionLink)
            assertEquals("magiclink", value.properties.verificationType)
            assertEquals("123456", value.properties.emailOtp)
            // ...and the remaining top-level fields become the embedded `user`.
            assertEquals("u1", value.user?.id)
            assertEquals("user@example.com", value.user?.email)
        }

    @Test
    fun test_signOut_usesProvidedJwtNotAdminKey() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.signOut(jwt = "user-jwt", scope = SignOutScope.GLOBAL)

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/logout?scope=global", client.lastPostEndpoint)
            assertEquals("Bearer user-jwt", client.lastPostHeaders["Authorization"])
        }

    @Test
    fun test_listOAuthClients_withPaging_decodesClientsAndSendsQueryParams() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.listOAuthClients(page = 3, perPage = 25)

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/oauth/clients", client.lastGetEndpoint)
            assertEquals(listOf("page" to "3", "per_page" to "25"), client.lastGetQueryParams)
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.OAuthClientListResponse
            assertEquals("oauth-client-1", value.clients.first().clientId)
        }

    @Test
    fun test_createOAuthClient_sendsCreatePayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result =
                sut.createOAuthClient(
                    OAuthClientCreateRequest(
                        clientName = "Demo Client",
                        redirectUris = listOf("https://app.example/callback"),
                        grantTypes = listOf("authorization_code", "refresh_token"),
                        responseTypes = listOf("code"),
                        tokenEndpointAuthMethod = OAuthClientTokenEndpointAuthMethod.CLIENT_SECRET_POST,
                    ),
                )

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/oauth/clients", client.lastPostEndpoint)
            val body = json.parseToJsonElement(client.lastPostBody ?: error("missing body")).jsonObject
            assertEquals("Demo Client", body["client_name"]?.jsonPrimitive?.content)
            assertEquals("client_secret_post", body["token_endpoint_auth_method"]?.jsonPrimitive?.content)
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.OAuthClient
            assertEquals("oauth-client-1", value.clientId)
        }

    @Test
    fun test_updateOAuthClient_sendsUpdatePayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result =
                sut.updateOAuthClient(
                    clientId = "oauth-client-1",
                    request = OAuthClientUpdateRequest(clientName = "Updated Client"),
                )

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/oauth/clients/oauth-client-1", client.lastPutEndpoint)
            val body = json.parseToJsonElement(client.lastPutBody ?: error("missing body")).jsonObject
            assertEquals("Updated Client", body["client_name"]?.jsonPrimitive?.content)
        }

    @Test
    fun test_getDeleteAndRegenerateOAuthClient_useExpectedEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val getResult = sut.getOAuthClient("oauth-client-1")
            val regenerateResult = sut.regenerateOAuthClientSecret("oauth-client-1")
            val deleteResult = sut.deleteOAuthClient("oauth-client-1")

            assertIs<SupabaseResult.Success<*>>(getResult)
            assertIs<SupabaseResult.Success<*>>(regenerateResult)
            assertIs<SupabaseResult.Success<*>>(deleteResult)
            assertEquals("/auth/v1/admin/oauth/clients/oauth-client-1", client.lastDeleteEndpoint)
            assertEquals("/auth/v1/admin/oauth/clients/oauth-client-1/regenerate_secret", client.lastPostEndpoint)
        }

    @Test
    fun test_listCustomProviders_withTypeFilter_decodesProviders() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.listCustomProviders(type = CustomProviderType.OIDC)

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/custom-providers", client.lastGetEndpoint)
            assertEquals(listOf("type" to "oidc"), client.lastGetQueryParams)
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.CustomProviderListResponse
            assertEquals("custom:acme", value.providers.first().identifier)
        }

    @Test
    fun test_createCustomProvider_sendsProviderPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result =
                sut.createCustomProvider(
                    CustomProviderCreateRequest(
                        providerType = CustomProviderType.OIDC,
                        identifier = "custom:acme",
                        name = "Acme",
                        clientId = "client-id",
                        clientSecret = "client-secret",
                        issuer = "https://issuer.example",
                        scopes = listOf("openid", "email"),
                        pkceEnabled = true,
                    ),
                )

            assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/custom-providers", client.lastPostEndpoint)
            val body = json.parseToJsonElement(client.lastPostBody ?: error("missing body")).jsonObject
            assertEquals("oidc", body["provider_type"]?.jsonPrimitive?.content)
            assertEquals("custom:acme", body["identifier"]?.jsonPrimitive?.content)
            assertEquals(true, body["pkce_enabled"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun test_getUpdateAndDeleteCustomProvider_useExpectedEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val getResult = sut.getCustomProvider("custom:acme")
            val updateResult =
                sut.updateCustomProvider(
                    identifier = "custom:acme",
                    request = CustomProviderUpdateRequest(name = "Acme Updated", enabled = false),
                )
            val deleteResult = sut.deleteCustomProvider("custom:acme")

            assertIs<SupabaseResult.Success<*>>(getResult)
            assertIs<SupabaseResult.Success<*>>(updateResult)
            assertIs<SupabaseResult.Success<*>>(deleteResult)
            assertEquals("/auth/v1/admin/custom-providers/custom:acme", client.lastDeleteEndpoint)
            val body = json.parseToJsonElement(client.lastPutBody ?: error("missing body")).jsonObject
            assertEquals("Acme Updated", body["name"]?.jsonPrimitive?.content)
            assertEquals(false, body["enabled"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun test_listSsoProviders_unwrapsItemsWrapper() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result = sut.listSsoProviders()

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/sso/providers", client.lastGetEndpoint)
            assertEquals("Bearer service-role", client.lastGetHeaders["Authorization"])
            val value = success.value as List<*>
            val provider = value.first() as io.github.androidpoet.supabase.auth.admin.models.SsoProvider
            assertEquals("sso-provider-1", provider.id)
            assertEquals("saml", provider.type)
            assertEquals("https://idp.example/metadata", provider.saml?.metadataUrl)
            assertEquals("acme.example", provider.domains.first().domain)
        }

    @Test
    fun test_createSsoProvider_sendsSamlPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val result =
                sut.createSsoProvider(
                    SsoProviderCreateRequest(
                        metadataUrl = "https://idp.example/metadata",
                        domains = listOf("acme.example"),
                    ),
                )

            val success = assertIs<SupabaseResult.Success<*>>(result)
            assertEquals("/auth/v1/admin/sso/providers", client.lastPostEndpoint)
            val body = json.parseToJsonElement(client.lastPostBody ?: error("missing body")).jsonObject
            assertEquals("saml", body["type"]?.jsonPrimitive?.content)
            assertEquals("https://idp.example/metadata", body["metadata_url"]?.jsonPrimitive?.content)
            val value = success.value as io.github.androidpoet.supabase.auth.admin.models.SsoProvider
            assertEquals("sso-provider-1", value.id)
        }

    @Test
    fun test_getUpdateAndDeleteSsoProvider_useExpectedEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = client.authAdmin(serviceRoleKey = "service-role")

            val getResult = sut.getSsoProvider("sso-provider-1")
            val updateResult =
                sut.updateSsoProvider(
                    id = "sso-provider-1",
                    request = SsoProviderUpdateRequest(metadataUrl = "https://idp.example/new"),
                )
            val deleteResult = sut.deleteSsoProvider("sso-provider-1")

            assertIs<SupabaseResult.Success<*>>(getResult)
            assertIs<SupabaseResult.Success<*>>(updateResult)
            val deleteSuccess = assertIs<SupabaseResult.Success<*>>(deleteResult)
            assertEquals("/auth/v1/admin/sso/providers/sso-provider-1", client.lastGetEndpoint)
            assertEquals("/auth/v1/admin/sso/providers/sso-provider-1", client.lastPutEndpoint)
            assertEquals("/auth/v1/admin/sso/providers/sso-provider-1", client.lastDeleteEndpoint)
            val body = json.parseToJsonElement(client.lastPutBody ?: error("missing body")).jsonObject
            assertEquals("https://idp.example/new", body["metadata_url"]?.jsonPrimitive?.content)
            val deleted = deleteSuccess.value as io.github.androidpoet.supabase.auth.admin.models.SsoProvider
            assertEquals("sso-provider-1", deleted.id)
        }
}

private class FakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    var lastGetEndpoint: String? = null
    var lastGetQueryParams: List<Pair<String, String>> = emptyList()
    var lastGetHeaders: Map<String, String> = emptyMap()
    var lastPostEndpoint: String? = null
    var lastPostBody: String? = null
    var lastPostHeaders: Map<String, String> = emptyMap()
    var lastPutEndpoint: String? = null
    var lastPutBody: String? = null
    var lastPutHeaders: Map<String, String> = emptyMap()
    var lastDeleteEndpoint: String? = null
    var lastDeleteBody: String? = null
    var lastDeleteHeaders: Map<String, String> = emptyMap()

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastGetEndpoint = endpoint
        lastGetQueryParams = queryParams
        lastGetHeaders = headers
        return when {
            endpoint == "/auth/v1/admin/oauth/clients" ->
                SupabaseResult.Success(
                    """{"clients":[${oauthClientJson()}],"aud":"authenticated"}""",
                )
            endpoint.startsWith("/auth/v1/admin/oauth/clients/") -> SupabaseResult.Success(oauthClientJson())
            endpoint == "/auth/v1/admin/custom-providers" ->
                SupabaseResult.Success(
                    """{"providers":[${customProviderJson()}]}""",
                )
            endpoint.startsWith("/auth/v1/admin/custom-providers/") -> SupabaseResult.Success(customProviderJson())
            endpoint == "/auth/v1/admin/sso/providers" ->
                SupabaseResult.Success(
                    """{"items":[${ssoProviderJson()}]}""",
                )
            endpoint.startsWith("/auth/v1/admin/sso/providers/") -> SupabaseResult.Success(ssoProviderJson())
            endpoint == "/auth/v1/admin/audit" ->
                SupabaseResult.Success(
                    """
                    [
                      {
                        "id": "audit1",
                        "payload": {"action": "login", "actor_id": "u1"},
                        "created_at": "2026-01-01T00:00:00Z",
                        "ip_address": "203.0.113.7"
                      }
                    ]
                    """.trimIndent(),
                )
            endpoint == "/auth/v1/admin/users" ->
                SupabaseResult.Success(
                    """{"users":[{"id":"u1","email":"user@example.com"}],"aud":"authenticated"}""",
                )
            endpoint.endsWith("/factors") ->
                SupabaseResult.Success(
                    """
                    {
                      "factors": [
                        {
                          "id": "factor1",
                          "friendly_name": "Auth App",
                          "factor_type": "totp",
                          "status": "verified"
                        },
                        {
                          "id": "factor2",
                          "friendly_name": "Security Key",
                          "factor_type": "webauthn",
                          "status": "unverified"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            endpoint.endsWith("/passkeys") ->
                SupabaseResult.Success(
                    """
                    [
                      {
                        "id": "passkey1",
                        "friendly_name": "Laptop",
                        "created_at": "2026-01-01T00:00:00Z",
                        "last_used_at": "2026-01-02T00:00:00Z"
                      }
                    ]
                    """.trimIndent(),
                )
            // GoTrue returns a bare User from this endpoint (no {"user":…} wrapper).
            endpoint.startsWith("/auth/v1/admin/users/") ->
                SupabaseResult.Success(
                    """{"id":"u1","email":"user@example.com"}""",
                )
            else -> SupabaseResult.Failure(SupabaseError("unexpected GET $endpoint"))
        }
    }

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        lastPostBody = body
        lastPostHeaders = headers
        return when {
            endpoint.startsWith("/auth/v1/logout") -> SupabaseResult.Success("{}")
            endpoint == "/auth/v1/admin/oauth/clients" -> SupabaseResult.Success(oauthClientJson())
            endpoint.endsWith("/regenerate_secret") ->
                SupabaseResult.Success(
                    oauthClientJson(clientSecret = "new-secret"),
                )
            endpoint == "/auth/v1/admin/custom-providers" -> SupabaseResult.Success(customProviderJson())
            endpoint == "/auth/v1/admin/sso/providers" -> SupabaseResult.Success(ssoProviderJson())
            // GoTrue returns a bare User from create/invite (no {"user":…} wrapper).
            endpoint == "/auth/v1/admin/users" ->
                SupabaseResult.Success(
                    """{"id":"u1","email":"user@example.com"}""",
                )
            endpoint.startsWith("/auth/v1/invite") ->
                SupabaseResult.Success(
                    """{"id":"u1","email":"invite@example.com"}""",
                )
            endpoint.startsWith("/auth/v1/admin/generate_link") ->
                // GoTrue returns a FLAT object — the user fields and the link fields are all at the
                // top level (no `properties`/`user` envelope). The client reshapes this.
                SupabaseResult.Success(
                    """
                    {
                      "id": "u1",
                      "email": "user@example.com",
                      "action_link": "https://example.com/action",
                      "email_otp": "123456",
                      "hashed_token": "hash",
                      "redirect_to": "app://callback",
                      "verification_type": "magiclink"
                    }
                    """.trimIndent(),
                )
            else -> SupabaseResult.Failure(SupabaseError("unexpected POST $endpoint"))
        }
    }

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPutEndpoint = endpoint
        lastPutBody = body
        lastPutHeaders = headers
        return when {
            endpoint.startsWith("/auth/v1/admin/oauth/clients/") -> SupabaseResult.Success(oauthClientJson())
            endpoint.startsWith("/auth/v1/admin/custom-providers/") -> SupabaseResult.Success(customProviderJson(name = "Acme Updated"))
            endpoint.startsWith("/auth/v1/admin/sso/providers/") -> SupabaseResult.Success(ssoProviderJson())
            endpoint.contains("/factors/") ->
                SupabaseResult.Success(
                    """
                    {
                      "id": "factor1",
                      "friendly_name": "Renamed Key",
                      "factor_type": "totp",
                      "status": "verified"
                    }
                    """.trimIndent(),
                )
            // updateUserById: deliberately the {"user":…} wrapper shape so tolerance for it stays covered.
            else -> SupabaseResult.Success("""{"user":{"id":"u1","phone":"+15555550100"}}""")
        }
    }

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun delete(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastDeleteEndpoint = endpoint
        lastDeleteBody = body
        lastDeleteHeaders = headers
        return when {
            endpoint.contains("/factors/") -> SupabaseResult.Success("""{"id":"factor1"}""")
            endpoint.startsWith("/auth/v1/admin/sso/providers/") -> SupabaseResult.Success(ssoProviderJson())
            else -> SupabaseResult.Success("{}")
        }
    }

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override suspend fun rawRequest(
        method: io.github.androidpoet.supabase.client.SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): io.github.androidpoet.supabase.core.result.SupabaseResult<io.github.androidpoet.supabase.client.SupabaseHttpResponse> =
        io.github.androidpoet.supabase.core.result.SupabaseResult.Failure(
            io.github.androidpoet.supabase.core.result
                .SupabaseError("rawRequest not supported in test fake"),
        )

    override fun close() = Unit

    private fun oauthClientJson(clientSecret: String? = "secret"): String {
        val secret = clientSecret?.let { ""","client_secret":"$it"""" }.orEmpty()
        return """
            {
              "client_id": "oauth-client-1",
              "client_name": "Demo Client",
              "client_type": "confidential",
              "token_endpoint_auth_method": "client_secret_basic",
              "registration_type": "manual",
              "client_uri": "https://app.example",
              "redirect_uris": ["https://app.example/callback"],
              "grant_types": ["authorization_code", "refresh_token"],
              "response_types": ["code"],
              "scope": "openid email"$secret
            }
            """.trimIndent()
    }

    private fun customProviderJson(name: String = "Acme"): String =
        """
        {
          "id": "provider-id",
          "provider_type": "oidc",
          "identifier": "custom:acme",
          "name": "$name",
          "client_id": "client-id",
          "scopes": ["openid", "email"],
          "pkce_enabled": true,
          "enabled": true,
          "issuer": "https://issuer.example",
          "discovery_document": {
            "issuer": "https://issuer.example",
            "authorization_endpoint": "https://issuer.example/auth",
            "token_endpoint": "https://issuer.example/token",
            "jwks_uri": "https://issuer.example/jwks"
          }
        }
        """.trimIndent()

    private fun ssoProviderJson(): String =
        """
        {
          "id": "sso-provider-1",
          "type": "saml",
          "resource_id": null,
          "disabled": false,
          "saml": {
            "entity_id": "https://idp.example/entity",
            "metadata_url": "https://idp.example/metadata",
            "attribute_mapping": {"keys": {}}
          },
          "domains": [
            {"id": "domain-1", "domain": "acme.example"}
          ],
          "created_at": "2026-01-01T00:00:00Z",
          "updated_at": "2026-01-02T00:00:00Z"
        }
        """.trimIndent()
}
