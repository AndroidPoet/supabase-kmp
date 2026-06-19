@file:OptIn(ExperimentalSerializationApi::class)

package io.github.androidpoet.supabase.auth.admin.models

import io.github.androidpoet.supabase.auth.models.MfaFactor
import io.github.androidpoet.supabase.auth.models.User
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class AdminUserAttributes(
    public val email: String? = null,
    public val phone: String? = null,
    public val password: String? = null,
    public val nonce: String? = null,
    @SerialName("user_metadata") public val userMetadata: JsonObject? = null,
    @SerialName("app_metadata") public val appMetadata: JsonObject? = null,
    @SerialName("email_confirm") public val emailConfirm: Boolean? = null,
    @SerialName("phone_confirm") public val phoneConfirm: Boolean? = null,
    @SerialName("ban_duration") public val banDuration: String? = null,
    public val role: String? = null,
) {
    // Mask the password so it never leaks into logs or crash reports.
    override fun toString(): String =
        "AdminUserAttributes(email=$email, phone=$phone, " +
            "password=${if (password == null) "null" else "***"}, nonce=$nonce, " +
            "userMetadata=$userMetadata, appMetadata=$appMetadata, emailConfirm=$emailConfirm, " +
            "phoneConfirm=$phoneConfirm, banDuration=$banDuration, role=$role)"
}

@Serializable
public enum class GenerateLinkType {
    @SerialName("signup")
    SIGNUP,

    @SerialName("invite")
    INVITE,

    @SerialName("magiclink")
    MAGIC_LINK,

    @SerialName("recovery")
    RECOVERY,

    @SerialName("email_change_current")
    EMAIL_CHANGE_CURRENT,

    @SerialName("email_change_new")
    EMAIL_CHANGE_NEW,
}

@Serializable
public data class GenerateLinkRequest(
    public val type: GenerateLinkType,
    public val email: String,
    public val password: String? = null,
    @SerialName("new_email") public val newEmail: String? = null,
    public val data: JsonObject? = null,
    @SerialName("redirect_to") public val redirectTo: String? = null,
)

@Serializable
public data class GenerateLinkResponse(
    public val properties: GenerateLinkProperties,
    public val user: User? = null,
)

@Serializable
public data class GenerateLinkProperties(
    @SerialName("action_link") public val actionLink: String,
    @SerialName("email_otp") public val emailOtp: String? = null,
    @SerialName("hashed_token") public val hashedToken: String? = null,
    @SerialName("redirect_to") public val redirectTo: String? = null,
    @SerialName("verification_type") public val verificationType: String? = null,
)

@Serializable
public data class ListUsersResponse(
    public val users: List<User> = emptyList(),
    public val aud: String? = null,
)

@Serializable
public data class MfaAdminListFactorsResponse(
    public val factors: List<MfaFactor> = emptyList(),
)

@Serializable
public data class MfaAdminDeleteFactorResponse(
    public val id: String,
)

@Serializable
public enum class OAuthClientType(
    public val value: String,
) {
    @SerialName("public")
    PUBLIC("public"),

    @SerialName("confidential")
    CONFIDENTIAL("confidential"),

    /** A client type the server returned that this version does not recognise. */
    @SerialName("unknown")
    UNKNOWN("unknown"),
}

@Serializable
public enum class OAuthClientRegistrationType(
    public val value: String,
) {
    @SerialName("dynamic")
    DYNAMIC("dynamic"),

    @SerialName("manual")
    MANUAL("manual"),

    /** A registration type the server returned that this version does not recognise. */
    @SerialName("unknown")
    UNKNOWN("unknown"),
}

@Serializable
public enum class OAuthClientTokenEndpointAuthMethod(
    public val value: String,
) {
    @SerialName("none")
    NONE("none"),

    @SerialName("client_secret_basic")
    CLIENT_SECRET_BASIC("client_secret_basic"),

    @SerialName("client_secret_post")
    CLIENT_SECRET_POST("client_secret_post"),

    /** An auth method the server returned that this version does not recognise. */
    @SerialName("unknown")
    UNKNOWN("unknown"),
}

@Serializable
public data class OAuthClient(
    @SerialName("client_id") public val clientId: String,
    @SerialName("client_name") public val clientName: String,
    @SerialName("client_secret") public val clientSecret: String? = null,
    @SerialName("client_type") public val clientType: OAuthClientType = OAuthClientType.UNKNOWN,
    @SerialName("token_endpoint_auth_method")
    public val tokenEndpointAuthMethod: OAuthClientTokenEndpointAuthMethod = OAuthClientTokenEndpointAuthMethod.UNKNOWN,
    @SerialName("registration_type")
    public val registrationType: OAuthClientRegistrationType = OAuthClientRegistrationType.UNKNOWN,
    @SerialName("client_uri") public val clientUri: String? = null,
    @SerialName("logo_uri") public val logoUri: String? = null,
    @SerialName("redirect_uris") public val redirectUris: List<String>,
    @SerialName("grant_types") public val grantTypes: List<String>,
    @SerialName("response_types") public val responseTypes: List<String>,
    public val scope: String? = null,
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
) {
    // Mask the client secret so it never leaks into logs or crash reports.
    override fun toString(): String =
        "OAuthClient(clientId=$clientId, clientName=$clientName, " +
            "clientSecret=${if (clientSecret == null) "null" else "***"}, clientType=$clientType, " +
            "tokenEndpointAuthMethod=$tokenEndpointAuthMethod, registrationType=$registrationType, " +
            "clientUri=$clientUri, logoUri=$logoUri, redirectUris=$redirectUris, " +
            "grantTypes=$grantTypes, responseTypes=$responseTypes, scope=$scope, " +
            "createdAt=$createdAt, updatedAt=$updatedAt)"
}

@Serializable
public data class OAuthClientCreateRequest(
    @SerialName("client_name") public val clientName: String,
    @SerialName("redirect_uris") public val redirectUris: List<String>,
    @SerialName("client_uri") public val clientUri: String? = null,
    @SerialName("grant_types") public val grantTypes: List<String>? = null,
    @SerialName("response_types") public val responseTypes: List<String>? = null,
    public val scope: String? = null,
    @SerialName("token_endpoint_auth_method") public val tokenEndpointAuthMethod: OAuthClientTokenEndpointAuthMethod? = null,
)

@Serializable
public data class OAuthClientUpdateRequest(
    @SerialName("client_name") public val clientName: String? = null,
    @SerialName("client_uri") public val clientUri: String? = null,
    @SerialName("logo_uri") public val logoUri: String? = null,
    @SerialName("redirect_uris") public val redirectUris: List<String>? = null,
    @SerialName("grant_types") public val grantTypes: List<String>? = null,
    @SerialName("token_endpoint_auth_method") public val tokenEndpointAuthMethod: OAuthClientTokenEndpointAuthMethod? = null,
)

@Serializable
public data class OAuthClientListResponse(
    public val clients: List<OAuthClient> = emptyList(),
    public val aud: String? = null,
)

@Serializable
public enum class CustomProviderType(
    public val value: String,
) {
    @SerialName("oauth2")
    OAUTH2("oauth2"),

    @SerialName("oidc")
    OIDC("oidc"),

    /** A provider type the server returned that this version does not recognise. */
    @SerialName("unknown")
    UNKNOWN("unknown"),
}

@Serializable
public data class OidcDiscoveryDocument(
    public val issuer: String,
    @SerialName("authorization_endpoint") public val authorizationEndpoint: String,
    @SerialName("token_endpoint") public val tokenEndpoint: String,
    @SerialName("jwks_uri") public val jwksUri: String,
    @SerialName("userinfo_endpoint") public val userinfoEndpoint: String? = null,
    @SerialName("revocation_endpoint") public val revocationEndpoint: String? = null,
    @SerialName("supported_scopes") public val supportedScopes: List<String>? = null,
    @SerialName("supported_response_types") public val supportedResponseTypes: List<String>? = null,
    @SerialName("supported_subject_types") public val supportedSubjectTypes: List<String>? = null,
    @SerialName("supported_id_token_signing_algs") public val supportedIdTokenSigningAlgs: List<String>? = null,
)

@Serializable
public data class CustomProvider(
    public val id: String,
    @SerialName("provider_type") public val providerType: CustomProviderType = CustomProviderType.UNKNOWN,
    public val identifier: String,
    public val name: String,
    @SerialName("client_id") public val clientId: String,
    @SerialName("acceptable_client_ids") public val acceptableClientIds: List<String>? = null,
    public val scopes: List<String>? = null,
    @SerialName("pkce_enabled") public val pkceEnabled: Boolean? = null,
    @SerialName("attribute_mapping") public val attributeMapping: JsonObject? = null,
    @SerialName("authorization_params") public val authorizationParams: Map<String, String>? = null,
    public val enabled: Boolean? = null,
    @SerialName("email_optional") public val emailOptional: Boolean? = null,
    public val issuer: String? = null,
    @SerialName("discovery_url") public val discoveryUrl: String? = null,
    @SerialName("skip_nonce_check") public val skipNonceCheck: Boolean? = null,
    @SerialName("authorization_url") public val authorizationUrl: String? = null,
    @SerialName("token_url") public val tokenUrl: String? = null,
    @SerialName("userinfo_url") public val userinfoUrl: String? = null,
    @SerialName("jwks_uri") public val jwksUri: String? = null,
    @SerialName("discovery_document") public val discoveryDocument: OidcDiscoveryDocument? = null,
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
)

@Serializable
public data class CustomProviderCreateRequest(
    @SerialName("provider_type") public val providerType: CustomProviderType,
    public val identifier: String,
    public val name: String,
    @SerialName("client_id") public val clientId: String,
    @SerialName("client_secret") public val clientSecret: String,
    @SerialName("acceptable_client_ids") public val acceptableClientIds: List<String>? = null,
    public val scopes: List<String>? = null,
    @SerialName("pkce_enabled") public val pkceEnabled: Boolean? = null,
    @SerialName("attribute_mapping") public val attributeMapping: JsonObject? = null,
    @SerialName("authorization_params") public val authorizationParams: Map<String, String>? = null,
    public val enabled: Boolean? = null,
    @SerialName("email_optional") public val emailOptional: Boolean? = null,
    public val issuer: String? = null,
    @SerialName("discovery_url") public val discoveryUrl: String? = null,
    @SerialName("skip_nonce_check") public val skipNonceCheck: Boolean? = null,
    @SerialName("authorization_url") public val authorizationUrl: String? = null,
    @SerialName("token_url") public val tokenUrl: String? = null,
    @SerialName("userinfo_url") public val userinfoUrl: String? = null,
    @SerialName("jwks_uri") public val jwksUri: String? = null,
) {
    // Mask the client secret so it never leaks into logs or crash reports.
    override fun toString(): String =
        "CustomProviderCreateRequest(providerType=$providerType, identifier=$identifier, name=$name, " +
            "clientId=$clientId, clientSecret=***, acceptableClientIds=$acceptableClientIds, " +
            "scopes=$scopes, pkceEnabled=$pkceEnabled, attributeMapping=$attributeMapping, " +
            "authorizationParams=$authorizationParams, enabled=$enabled, emailOptional=$emailOptional, " +
            "issuer=$issuer, discoveryUrl=$discoveryUrl, skipNonceCheck=$skipNonceCheck, " +
            "authorizationUrl=$authorizationUrl, tokenUrl=$tokenUrl, userinfoUrl=$userinfoUrl, " +
            "jwksUri=$jwksUri)"
}

@Serializable
public data class CustomProviderUpdateRequest(
    public val name: String? = null,
    @SerialName("client_id") public val clientId: String? = null,
    @SerialName("client_secret") public val clientSecret: String? = null,
    @SerialName("acceptable_client_ids") public val acceptableClientIds: List<String>? = null,
    public val scopes: List<String>? = null,
    @SerialName("pkce_enabled") public val pkceEnabled: Boolean? = null,
    @SerialName("attribute_mapping") public val attributeMapping: JsonObject? = null,
    @SerialName("authorization_params") public val authorizationParams: Map<String, String>? = null,
    public val enabled: Boolean? = null,
    @SerialName("email_optional") public val emailOptional: Boolean? = null,
    public val issuer: String? = null,
    @SerialName("discovery_url") public val discoveryUrl: String? = null,
    @SerialName("skip_nonce_check") public val skipNonceCheck: Boolean? = null,
    @SerialName("authorization_url") public val authorizationUrl: String? = null,
    @SerialName("token_url") public val tokenUrl: String? = null,
    @SerialName("userinfo_url") public val userinfoUrl: String? = null,
    @SerialName("jwks_uri") public val jwksUri: String? = null,
) {
    // Mask the client secret so it never leaks into logs or crash reports.
    override fun toString(): String =
        "CustomProviderUpdateRequest(name=$name, clientId=$clientId, " +
            "clientSecret=${if (clientSecret == null) "null" else "***"}, " +
            "acceptableClientIds=$acceptableClientIds, scopes=$scopes, pkceEnabled=$pkceEnabled, " +
            "attributeMapping=$attributeMapping, authorizationParams=$authorizationParams, " +
            "enabled=$enabled, emailOptional=$emailOptional, issuer=$issuer, " +
            "discoveryUrl=$discoveryUrl, skipNonceCheck=$skipNonceCheck, " +
            "authorizationUrl=$authorizationUrl, tokenUrl=$tokenUrl, userinfoUrl=$userinfoUrl, " +
            "jwksUri=$jwksUri)"
}

@Serializable
public data class CustomProviderListResponse(
    public val providers: List<CustomProvider> = emptyList(),
)

@Serializable
public data class SsoProviderSaml(
    @SerialName("entity_id") public val entityId: String? = null,
    @SerialName("metadata_url") public val metadataUrl: String? = null,
    @SerialName("metadata_xml") public val metadataXml: String? = null,
    @SerialName("attribute_mapping") public val attributeMapping: JsonObject? = null,
    @SerialName("name_id_format") public val nameIdFormat: String? = null,
)

@Serializable
public data class SsoDomain(
    public val id: String? = null,
    public val domain: String? = null,
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
)

@Serializable
public data class SsoProvider(
    public val id: String,
    public val type: String? = null,
    @SerialName("resource_id") public val resourceId: String? = null,
    public val disabled: Boolean? = null,
    public val saml: SsoProviderSaml? = null,
    public val domains: List<SsoDomain> = emptyList(),
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
)

@Serializable
public data class SsoProviderCreateRequest(
    // The server requires `type` on the wire; force it even though it has a default.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) public val type: String = "saml",
    @SerialName("metadata_url") public val metadataUrl: String? = null,
    @SerialName("metadata_xml") public val metadataXml: String? = null,
    public val domains: List<String>? = null,
    @SerialName("attribute_mapping") public val attributeMapping: JsonObject? = null,
    @SerialName("name_id_format") public val nameIdFormat: String? = null,
    @SerialName("resource_id") public val resourceId: String? = null,
    public val disabled: Boolean? = null,
)

@Serializable
public data class SsoProviderUpdateRequest(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) public val type: String = "saml",
    @SerialName("metadata_url") public val metadataUrl: String? = null,
    @SerialName("metadata_xml") public val metadataXml: String? = null,
    public val domains: List<String>? = null,
    @SerialName("attribute_mapping") public val attributeMapping: JsonObject? = null,
    @SerialName("name_id_format") public val nameIdFormat: String? = null,
    @SerialName("resource_id") public val resourceId: String? = null,
    public val disabled: Boolean? = null,
)

@Serializable
public data class SsoProviderListResponse(
    public val items: List<SsoProvider> = emptyList(),
)

@Serializable
public data class Passkey(
    public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("last_used_at") public val lastUsedAt: String? = null,
)

/**
 * A single audit-log entry returned by `GET /admin/audit`.
 *
 * The [payload] is a free-form object whose shape depends on the recorded [payload]'s `action`,
 * so it is kept as a raw [JsonObject] rather than a strongly typed model.
 */
@Serializable
public data class AuditLogEntry(
    public val id: String,
    public val payload: JsonObject? = null,
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("ip_address") public val ipAddress: String? = null,
)

@Serializable
internal data class UpdateFactorRequest(
    @SerialName("friendly_name") val friendlyName: String? = null,
)

@Serializable
internal data class InviteUserRequest(
    val email: String,
    val data: JsonObject? = null,
)

@Serializable
internal data class UserDeleteRequest(
    @SerialName("should_soft_delete") val shouldSoftDelete: Boolean,
)
