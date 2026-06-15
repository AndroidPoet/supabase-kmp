package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.LinkIdentityResponse
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaUnenrollResponse
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.OAuthAuthorizationDetails
import io.github.androidpoet.supabase.auth.models.OAuthGrant
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OAuthRedirect
import io.github.androidpoet.supabase.auth.models.OAuthResponse
import io.github.androidpoet.supabase.auth.models.OtpType
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
import io.github.androidpoet.supabase.auth.models.Passkey
import io.github.androidpoet.supabase.auth.models.PasskeyAuthenticationOptionsResponse
import io.github.androidpoet.supabase.auth.models.PasskeyMetadata
import io.github.androidpoet.supabase.auth.models.PasskeyRegistrationOptionsResponse
import io.github.androidpoet.supabase.auth.models.PkceParams
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.SsoResponse
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserIdentity
import io.github.androidpoet.supabase.auth.models.UserUpdateRequest
import io.github.androidpoet.supabase.auth.models.Web3Chain
import io.github.androidpoet.supabase.auth.session.SessionManager
import io.github.androidpoet.supabase.auth.session.SessionState
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthClientExtTest {
    @Test
    fun test_signUp_alias_routesToEmailSignup() =
        runTest {
            val auth = FakeAuthClient()

            auth.signUp(email = "a@b.com", password = "secret")

            assertEquals("a@b.com", auth.lastEmailSignUp)
        }

    @Test
    fun test_signIn_alias_routesToEmailSignIn() =
        runTest {
            val auth = FakeAuthClient()

            auth.signIn(email = "a@b.com", password = "secret")

            assertEquals("a@b.com", auth.lastEmailSignIn)
        }

    @Test
    fun test_signUpPhone_alias_routesToPhoneSignup() =
        runTest {
            val auth = FakeAuthClient()

            auth.signUpPhone(phone = "+10000000000", password = "secret")

            assertEquals("+10000000000", auth.lastPhoneSignUp)
        }

    @Test
    fun test_forgotPassword_alias_routesToResetPassword() =
        runTest {
            val auth = FakeAuthClient()

            val result = auth.forgotPassword(email = "a@b.com")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("a@b.com", auth.lastResetPasswordEmail)
        }

    @Test
    fun test_resendSignUpEmailOtp_routesWithEmailType() =
        runTest {
            val auth = FakeAuthClient()

            auth.resendSignUpEmailOtp(email = "a@b.com")

            assertEquals(OtpType.EMAIL, auth.lastResendEmailType)
            assertEquals("a@b.com", auth.lastResendEmail)
        }

    @Test
    fun test_verifyEmailSignUpOtp_usesEmailOtpType() =
        runTest {
            val auth = FakeAuthClient()

            auth.verifyEmailSignUpOtp(email = "a@b.com", token = "123456")

            assertEquals(OtpType.EMAIL, auth.lastVerifyOtpType)
        }

    @Test
    fun test_signOutGlobal_usesGlobalScope() =
        runTest {
            val auth = FakeAuthClient()

            auth.signOutGlobal("token-1")

            assertEquals(SignOutScope.GLOBAL, auth.lastSignOutScope)
        }

    @Test
    fun test_verifyEmailSignUpOtpWithResult_usesEmailOtpType() =
        runTest {
            val auth = FakeAuthClient()

            auth.verifyEmailSignUpOtpWithResult(email = "a@b.com", token = "123456")

            assertEquals(OtpType.EMAIL, auth.lastVerifyOtpWithResultType)
        }

    @Test
    fun test_verifyPhoneSignInOtpWithResult_usesSmsOtpType() =
        runTest {
            val auth = FakeAuthClient()

            auth.verifyPhoneSignInOtpWithResult(phone = "+10000000000", token = "123456")

            assertEquals(OtpType.SMS, auth.lastVerifyOtpWithResultType)
        }

    @Test
    fun test_verifyEmailOtpWithTokenHashWithResult_routesToTokenHashApi() =
        runTest {
            val auth = FakeAuthClient()

            auth.verifyEmailOtpWithTokenHashWithResult(
                tokenHash = "hash-1",
                type = OtpType.RECOVERY,
            )

            assertEquals("hash-1", auth.lastVerifyOtpTokenHashWithResult)
        }

    @Test
    fun test_verifyOtpWithResultAndSaveSession_savesWhenAuthenticated() =
        runTest {
            val auth =
                FakeAuthClient().apply {
                    verifyWithResultValue =
                        OtpVerifyResult.Authenticated(
                            dummySession.copy(accessToken = "otp-result-acc", refreshToken = "otp-result-ref"),
                        )
                }
            val sessionManager = FakeSessionManager()

            val result =
                auth.verifyOtpWithResultAndSaveSession(
                    sessionManager = sessionManager,
                    email = "a@b.com",
                    token = "123456",
                    type = OtpType.EMAIL,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("otp-result-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_verifyOtpWithResultAndSaveSession_doesNotSaveWhenNoSession() =
        runTest {
            val auth =
                FakeAuthClient().apply {
                    verifyWithResultValue = OtpVerifyResult.VerifiedNoSession
                }
            val sessionManager = FakeSessionManager()

            val result =
                auth.verifyOtpWithResultAndSaveSession(
                    sessionManager = sessionManager,
                    email = "a@b.com",
                    token = "123456",
                    type = OtpType.EMAIL,
                )

            assertTrue(result is SupabaseResult.Success)
            assertTrue(sessionManager.currentSession == null)
        }

    @Test
    fun test_getUserForCurrentSession_updatesStoredSessionWhenRequested() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager =
                FakeSessionManager(
                    session =
                        auth.dummySession.copy(
                            accessToken = "token-123",
                            user = auth.dummySession.user.copy(email = "old@b.com"),
                        ),
                )

            val result =
                auth.getUserForCurrentSession(
                    sessionManager = sessionManager,
                    updateStoredSession = true,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-123", auth.lastGetUserAccessToken)
            assertEquals("updated@b.com", sessionManager.currentSession?.user?.email)
        }

    @Test
    fun test_reauthenticateCurrentSession_usesCurrentAccessToken() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager(session = auth.dummySession.copy(accessToken = "token-reauth"))

            val result = auth.reauthenticateCurrentSession(sessionManager)

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-reauth", auth.lastReauthenticateAccessToken)
        }

    @Test
    fun test_signOutCurrentSession_clearsStoredSessionOnSuccess() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager(session = auth.dummySession.copy(accessToken = "token-signout"))

            val result =
                auth.signOutCurrentSession(
                    sessionManager = sessionManager,
                    scope = SignOutScope.OTHERS,
                    clearStoredSessionOnSuccess = true,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-signout", auth.lastSignOutAccessToken)
            assertEquals(SignOutScope.OTHERS, auth.lastSignOutScope)
            assertFalse(sessionManager.wasAuthenticated)
        }

    @Test
    fun test_signOutCurrentSession_withoutSession_succeedsByDefault() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result = auth.signOutCurrentSession(sessionManager = sessionManager)

            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_signOutCurrentSession_withoutSession_canFailWhenStrict() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.signOutCurrentSession(
                    sessionManager = sessionManager,
                    succeedIfNoSession = false,
                )

            assertTrue(result is SupabaseResult.Failure)
        }

    @Test
    fun test_parseSessionTokensFromFragment_parsesRequiredFields() {
        val parsed =
            parseSessionTokensFromFragment(
                "access_token=acc-1&refresh_token=ref-1&expires_in=3600&token_type=bearer",
            )

        assertTrue(parsed is SupabaseResult.Success)
        assertEquals("acc-1", parsed.value.accessToken)
        assertEquals("ref-1", parsed.value.refreshToken)
        assertEquals(3600L, parsed.value.expiresIn)
        assertEquals("bearer", parsed.value.tokenType)
    }

    @Test
    fun test_importSessionFromUrl_retrievesUserAndSavesSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.importSessionFromUrl(
                    url = "myapp://cb#access_token=acc-2&refresh_token=ref-2&expires_in=7200&token_type=bearer",
                    sessionManager = sessionManager,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("acc-2", auth.lastGetUserAccessToken)
            val stored = sessionManager.currentSession
            assertNotNull(stored)
            assertEquals("acc-2", stored.accessToken)
            assertEquals("ref-2", stored.refreshToken)
            assertEquals(7200L, stored.expiresIn)
            assertEquals("updated@b.com", stored.user.email)
        }

    @Test
    fun test_exchangeCodeForSessionAndSave_savesExchangedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.exchangeCodeForSessionAndSave(
                    authCode = "code-1",
                    codeVerifier = "verifier-1",
                    sessionManager = sessionManager,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("code-1", auth.lastExchangeAuthCode)
            assertEquals("verifier-1", auth.lastExchangeCodeVerifier)
            assertEquals("exch-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_parseJwtClaims_parsesPayloadObject() {
        val header = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
        val payload = "eyJzdWIiOiJ1c2VyLTEiLCJyb2xlIjoiYXV0aGVudGljYXRlZCJ9"
        val jwt = "$header.$payload."

        val result = parseJwtClaims(jwt)

        assertTrue(result is SupabaseResult.Success)
        assertEquals("user-1", result.value["sub"]?.toString()?.trim('"'))
        assertEquals("authenticated", result.value["role"]?.toString()?.trim('"'))
    }

    @Test
    fun test_parseCurrentSessionJwtClaims_usesSessionAccessToken() =
        runTest {
            val header = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
            val payload = "eyJleHAiOjE3MDAwMDAwMDAsInN1YiI6InUxIn0"
            val jwt = "$header.$payload."
            val sessionManager =
                FakeSessionManager(
                    session = FakeAuthClient().dummySession.copy(accessToken = jwt),
                )

            val result = sessionManager.parseCurrentSessionJwtClaims()

            assertTrue(result is SupabaseResult.Success)
            assertEquals("u1", result.value["sub"]?.toString()?.trim('"'))
        }

    @Test
    fun test_unlinkIdentityAndUpdateSession_removesIdentityFromStoredSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager =
                FakeSessionManager(
                    session =
                        auth.dummySession.copy(
                            accessToken = "token-unlink",
                            user =
                                auth.dummySession.user.copy(
                                    identities =
                                        listOf(
                                            UserIdentity(id = "i-1", provider = "google"),
                                            UserIdentity(id = "i-2", provider = "github"),
                                        ),
                                ),
                        ),
                )

            val result =
                auth.unlinkIdentityAndUpdateSession(
                    accessToken = "token-unlink",
                    identityId = "i-1",
                    sessionManager = sessionManager,
                    updateStoredSession = true,
                )

            assertTrue(result is SupabaseResult.Success)
            val identities = sessionManager.currentSession?.user?.identities
            assertNotNull(identities)
            assertEquals(1, identities.size)
            assertEquals("i-2", identities.first().id)
        }

    @Test
    fun test_getUserIdentitiesForCurrentSession_usesSessionAccessToken() =
        runTest {
            val auth = FakeAuthClient()
            auth.currentUserIdentities =
                listOf(
                    UserIdentity(id = "provider-user-1", identityId = "identity-1", provider = "github"),
                )
            val sessionManager = FakeSessionManager(session = auth.dummySession.copy(accessToken = "token-identities"))

            val result = auth.getUserIdentitiesForCurrentSession(sessionManager)

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-identities", auth.lastGetIdentitiesAccessToken)
            assertEquals("identity-1", result.value.first().identityId)
        }

    @Test
    fun test_linkIdentityForCurrentSession_usesSessionAccessToken() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager(session = auth.dummySession.copy(accessToken = "token-link"))

            val result =
                auth.linkIdentityForCurrentSession(
                    sessionManager = sessionManager,
                    provider = OAuthProvider.GITHUB,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-link", auth.lastLinkIdentityAccessToken)
            assertEquals(OAuthProvider.GITHUB, auth.lastLinkIdentityProvider)
        }

    @Test
    fun test_linkIdentityWithIdTokenForCurrentSession_savesSessionOnSuccess() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager(session = auth.dummySession.copy(accessToken = "token-link-id"))

            val result =
                auth.linkIdentityWithIdTokenForCurrentSession(
                    sessionManager = sessionManager,
                    provider = OAuthProvider.GOOGLE,
                    idToken = "id-token-123",
                    providerAccessToken = "provider-token-xyz",
                    nonce = "nonce-1",
                    updateStoredSession = true,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-link-id", auth.lastLinkIdTokenAccessToken)
            assertEquals("id-token-123", auth.lastLinkIdTokenValue)
            assertEquals("linked-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_unlinkIdentityForCurrentSession_usesSessionAccessToken() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager =
                FakeSessionManager(
                    session =
                        auth.dummySession.copy(
                            accessToken = "token-unlink-current",
                            user =
                                auth.dummySession.user.copy(
                                    identities = listOf(UserIdentity(id = "i-1"), UserIdentity(id = "i-2")),
                                ),
                        ),
                )

            val result =
                auth.unlinkIdentityForCurrentSession(
                    sessionManager = sessionManager,
                    identityId = "i-1",
                    updateStoredSession = true,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-unlink-current", auth.lastUnlinkAccessToken)
            assertEquals("i-1", auth.lastUnlinkIdentityId)
            assertEquals(
                listOf("i-2"),
                sessionManager.currentSession
                    ?.user
                    ?.identities
                    ?.map { it.id },
            )
        }

    @Test
    fun test_updateUserForCurrentSession_updatesStoredSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager(session = auth.dummySession.copy(accessToken = "token-update-current"))

            val result =
                auth.updateUserForCurrentSession(
                    sessionManager = sessionManager,
                    updates = UserUpdateRequest(email = "new@b.com"),
                    updateStoredSession = true,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("token-update-current", auth.lastUpdateUserAccessToken)
            assertEquals("new@b.com", sessionManager.currentSession?.user?.email)
        }

    @Test
    fun test_refreshCurrentSession_delegatesToSessionManager() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager =
                FakeSessionManager(session = auth.dummySession).apply {
                    refreshResult = SupabaseResult.Success(auth.dummySession.copy(accessToken = "refreshed-token"))
                }

            val result = auth.refreshCurrentSession(sessionManager)

            assertTrue(result is SupabaseResult.Success)
            assertEquals("refreshed-token", result.value.accessToken)
            assertTrue(sessionManager.refreshCalled)
        }

    @Test
    fun test_importAuthToken_retrievesUserAndSavesSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.importAuthToken(
                    sessionManager = sessionManager,
                    accessToken = "import-acc",
                    refreshToken = "import-ref",
                    expiresIn = 1234L,
                    tokenType = "bearer",
                    retrieveUser = true,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("import-acc", auth.lastGetUserAccessToken)
            assertEquals("import-acc", sessionManager.currentSession?.accessToken)
            assertEquals("import-ref", sessionManager.currentSession?.refreshToken)
            assertEquals(1234L, sessionManager.currentSession?.expiresIn)
        }

    @Test
    fun test_retrieveSsoUrlForCurrentSession_usesSessionAccessToken() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager(session = auth.dummySession.copy(accessToken = "sso-token"))

            val result =
                auth.retrieveSsoUrlForCurrentSession(
                    sessionManager = sessionManager,
                    domain = "example.com",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("sso-token", auth.lastRetrieveSsoAccessToken)
        }

    @Test
    fun test_signInWithEmailAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.signInWithEmailAndSaveSession(
                    sessionManager = sessionManager,
                    email = "a@b.com",
                    password = "secret",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("a@b.com", auth.lastEmailSignIn)
            assertEquals("sign-in-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_signUpWithEmailAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.signUpWithEmailAndSaveSession(
                    sessionManager = sessionManager,
                    email = "a@b.com",
                    password = "secret",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("a@b.com", auth.lastEmailSignUp)
            assertEquals("sign-up-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_signInAnonymouslyAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.signInAnonymouslyAndSaveSession(
                    sessionManager = sessionManager,
                    captchaToken = "captcha-1",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("anon-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_refreshTokenAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.refreshTokenAndSaveSession(
                    sessionManager = sessionManager,
                    refreshToken = "refresh-1",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("refresh-1", auth.lastRefreshTokenInput)
            assertEquals("refresh-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_verifyOtpAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.verifyOtpAndSaveSession(
                    sessionManager = sessionManager,
                    email = "a@b.com",
                    token = "123456",
                    type = OtpType.EMAIL,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals(OtpType.EMAIL, auth.lastVerifyOtpType)
            assertEquals("verify-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_signInWithPhoneAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.signInWithPhoneAndSaveSession(
                    sessionManager = sessionManager,
                    phone = "+10000000000",
                    password = "secret",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("phone-sign-in-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_signUpWithPhoneAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.signUpWithPhoneAndSaveSession(
                    sessionManager = sessionManager,
                    phone = "+10000000000",
                    password = "secret",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("phone-sign-up-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_signInWithIdTokenAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.signInWithIdTokenAndSaveSession(
                    sessionManager = sessionManager,
                    provider = OAuthProvider.GOOGLE,
                    idToken = "id-token-1",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("id-token-sign-in-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_verifyOtpWithTokenHashAndSaveSession_savesReturnedSession() =
        runTest {
            val auth = FakeAuthClient()
            val sessionManager = FakeSessionManager()

            val result =
                auth.verifyOtpWithTokenHashAndSaveSession(
                    sessionManager = sessionManager,
                    tokenHash = "hash-123",
                    type = OtpType.EMAIL,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("verify-hash-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_verifyOtpWithTokenHashWithResultAndSaveSession_savesWhenAuthenticated() =
        runTest {
            val auth =
                FakeAuthClient().apply {
                    verifyWithResultValue =
                        OtpVerifyResult.Authenticated(
                            dummySession.copy(accessToken = "verify-hash-result-acc", refreshToken = "verify-hash-result-ref"),
                        )
                }
            val sessionManager = FakeSessionManager()

            val result =
                auth.verifyOtpWithTokenHashWithResultAndSaveSession(
                    sessionManager = sessionManager,
                    tokenHash = "hash-123",
                    type = OtpType.EMAIL,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("verify-hash-result-acc", sessionManager.currentSession?.accessToken)
        }

    @Test
    fun test_mfaChallengeAndVerify_usesChallengeIdFromChallengeResponse() =
        runTest {
            val auth = FakeAuthClient()

            val result =
                auth.mfaChallengeAndVerify(
                    factorId = "factor-1",
                    code = "123456",
                    accessToken = "token-mfa",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("factor-1", auth.lastMfaChallengeFactorId)
            assertEquals("challenge-1", auth.lastMfaVerifyChallengeId)
            assertEquals("123456", auth.lastMfaVerifyCode)
            assertEquals("token-mfa", auth.lastMfaVerifyAccessToken)
        }
}

private class FakeAuthClient : AuthClient {
    var lastEmailSignUp: String? = null
    var lastEmailSignIn: String? = null
    var lastPhoneSignUp: String? = null
    var lastResetPasswordEmail: String? = null
    var lastResendEmailType: OtpType? = null
    var lastResendEmail: String? = null
    var lastVerifyOtpType: OtpType? = null
    var lastVerifyOtpWithResultType: OtpType? = null
    var lastVerifyOtpTokenHashWithResult: String? = null
    var verifyWithResultValue: OtpVerifyResult = OtpVerifyResult.VerifiedNoSession
    var lastSignOutScope: SignOutScope? = null
    var lastSignOutAccessToken: String? = null
    var lastGetUserAccessToken: String? = null
    var lastReauthenticateAccessToken: String? = null
    var lastExchangeAuthCode: String? = null
    var lastExchangeCodeVerifier: String? = null
    var lastLinkIdentityAccessToken: String? = null
    var lastLinkIdentityProvider: OAuthProvider? = null
    var lastLinkIdTokenAccessToken: String? = null
    var lastLinkIdTokenValue: String? = null
    var lastUnlinkAccessToken: String? = null
    var lastUnlinkIdentityId: String? = null
    var lastGetIdentitiesAccessToken: String? = null
    var lastUpdateUserAccessToken: String? = null
    var lastRetrieveSsoAccessToken: String? = null
    var lastRefreshTokenInput: String? = null
    var lastMfaChallengeFactorId: String? = null
    var lastMfaVerifyChallengeId: String? = null
    var lastMfaVerifyCode: String? = null
    var lastMfaVerifyAccessToken: String? = null
    var currentUserIdentities: List<UserIdentity> = emptyList()

    val dummySession =
        Session(
            accessToken = "a",
            tokenType = "bearer",
            expiresIn = 3600,
            refreshToken = "r",
            user =
                User(
                    id = "u1",
                    email = "a@b.com",
                ),
        )

    override suspend fun signUpWithEmail(email: String, password: String, data: JsonObject?): SupabaseResult<Session> {
        lastEmailSignUp = email
        return SupabaseResult.Success(dummySession.copy(accessToken = "sign-up-acc", refreshToken = "sign-up-ref"))
    }

    override suspend fun signUpWithPhone(phone: String, password: String, data: JsonObject?): SupabaseResult<Session> {
        lastPhoneSignUp = phone
        return SupabaseResult.Success(dummySession.copy(accessToken = "phone-sign-up-acc", refreshToken = "phone-sign-up-ref"))
    }

    override suspend fun signInWithEmail(email: String, password: String): SupabaseResult<Session> {
        lastEmailSignIn = email
        return SupabaseResult.Success(dummySession.copy(accessToken = "sign-in-acc", refreshToken = "sign-in-ref"))
    }

    override suspend fun signInAnonymously(data: JsonObject?, captchaToken: String?): SupabaseResult<Session> =
        SupabaseResult.Success(dummySession.copy(accessToken = "anon-acc", refreshToken = "anon-ref"))

    override suspend fun signInWithPhone(phone: String, password: String): SupabaseResult<Session> =
        SupabaseResult.Success(dummySession.copy(accessToken = "phone-sign-in-acc", refreshToken = "phone-sign-in-ref"))

    override suspend fun signInWithIdToken(
        provider: OAuthProvider,
        idToken: String,
        accessToken: String?,
        nonce: String?,
        captchaToken: String?,
    ): SupabaseResult<Session> =
        SupabaseResult.Success(
            dummySession.copy(accessToken = "id-token-sign-in-acc", refreshToken = "id-token-sign-in-ref"),
        )

    override suspend fun signInWithWeb3(
        chain: Web3Chain,
        message: String,
        signature: String,
        captchaToken: String?,
    ): SupabaseResult<Session> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun signInWithOtp(
        email: String?,
        phone: String?,
        createUser: Boolean?,
        captchaToken: String?,
        emailRedirectTo: String?,
    ): SupabaseResult<Unit> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun verifyOtp(
        email: String?,
        phone: String?,
        token: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<Session> =
        SupabaseResult.Success(dummySession.copy(accessToken = "verify-acc", refreshToken = "verify-ref")).also {
            lastVerifyOtpType = type
        }

    override suspend fun verifyOtpWithTokenHash(
        tokenHash: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<Session> =
        SupabaseResult.Success(dummySession.copy(accessToken = "verify-hash-acc", refreshToken = "verify-hash-ref"))

    override suspend fun verifyOtpWithResult(
        email: String?,
        phone: String?,
        token: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<OtpVerifyResult> =
        SupabaseResult.Success(verifyWithResultValue).also {
            lastVerifyOtpWithResultType = type
        }

    override suspend fun verifyOtpWithTokenHashWithResult(
        tokenHash: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<OtpVerifyResult> =
        SupabaseResult.Success(verifyWithResultValue).also {
            lastVerifyOtpTokenHashWithResult = tokenHash
        }

    override suspend fun resendEmailOtp(type: OtpType, email: String, captchaToken: String?, redirectTo: String?): SupabaseResult<Unit> =
        SupabaseResult.Success(Unit).also {
            lastResendEmailType = type
            lastResendEmail = email
        }

    override suspend fun resendPhoneOtp(type: OtpType, phone: String, captchaToken: String?): SupabaseResult<Unit> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun resetPasswordForEmail(email: String, redirectTo: String?, captchaToken: String?): SupabaseResult<Unit> {
        lastResetPasswordEmail = email
        return SupabaseResult.Success(Unit)
    }

    override suspend fun reauthenticate(accessToken: String): SupabaseResult<Unit> =
        SupabaseResult.Success(Unit).also {
            lastReauthenticateAccessToken = accessToken
        }

    override suspend fun refreshToken(refreshToken: String): SupabaseResult<Session> =
        SupabaseResult.Success(dummySession.copy(accessToken = "refresh-acc", refreshToken = refreshToken)).also {
            lastRefreshTokenInput = refreshToken
        }

    override suspend fun getUser(accessToken: String): SupabaseResult<User> =
        SupabaseResult.Success(dummySession.user.copy(email = "updated@b.com")).also {
            lastGetUserAccessToken = accessToken
        }

    override suspend fun getUserIdentities(accessToken: String): SupabaseResult<List<UserIdentity>> =
        SupabaseResult.Success(currentUserIdentities).also {
            lastGetIdentitiesAccessToken = accessToken
        }

    override suspend fun updateUser(accessToken: String, updates: UserUpdateRequest): SupabaseResult<User> =
        SupabaseResult.Success(dummySession.user.copy(email = updates.email ?: dummySession.user.email)).also {
            lastUpdateUserAccessToken = accessToken
        }

    override suspend fun signOut(accessToken: String): SupabaseResult<Unit> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun signOut(accessToken: String, scope: SignOutScope): SupabaseResult<Unit> =
        SupabaseResult.Success(Unit).also {
            lastSignOutScope = scope
            lastSignOutAccessToken = accessToken
        }

    override suspend fun linkIdentity(accessToken: String, provider: OAuthProvider, redirectTo: String?, scopes: List<String>, queryParams: Map<String, String>): SupabaseResult<LinkIdentityResponse> =
        SupabaseResult.Success(LinkIdentityResponse(url = "https://example.com/link", provider = provider.value)).also {
            lastLinkIdentityAccessToken = accessToken
            lastLinkIdentityProvider = provider
        }

    override suspend fun linkIdentityWithIdToken(
        accessToken: String,
        provider: OAuthProvider,
        idToken: String,
        providerAccessToken: String?,
        nonce: String?,
    ): SupabaseResult<Session> =
        SupabaseResult
            .Success(
                dummySession.copy(accessToken = "linked-acc", refreshToken = "linked-ref"),
            ).also {
                lastLinkIdTokenAccessToken = accessToken
                lastLinkIdTokenValue = idToken
            }

    override suspend fun unlinkIdentity(accessToken: String, identityId: String): SupabaseResult<Unit> =
        SupabaseResult.Success(Unit).also {
            lastUnlinkAccessToken = accessToken
            lastUnlinkIdentityId = identityId
        }

    override suspend fun retrieveSsoUrl(accessToken: String?, domain: String?, providerId: String?, redirectTo: String?): SupabaseResult<SsoResponse> =
        SupabaseResult.Success(SsoResponse(url = "https://example.com/sso")).also {
            lastRetrieveSsoAccessToken = accessToken
        }

    override suspend fun signInWithOAuth(
        provider: OAuthProvider,
        redirectTo: String?,
        scopes: List<String>,
        queryParams: Map<String, String>,
        skipBrowserRedirect: Boolean,
        pkceParams: PkceParams?,
    ): SupabaseResult<OAuthResponse> =
        SupabaseResult.Success(OAuthResponse(provider = provider.value, url = "https://example.com/oauth"))

    override fun getOAuthSignInUrl(provider: OAuthProvider, redirectTo: String?, scopes: List<String>, queryParams: Map<String, String>, skipBrowserRedirect: Boolean, pkceParams: PkceParams?): String =
        "https://example.com/oauth"

    override fun generatePkceParams(sha256: ((ByteArray) -> ByteArray)?): PkceParams =
        PkceParams(codeVerifier = "v", codeChallenge = "c", codeChallengeMethod = "S256")

    override suspend fun exchangeCodeForSession(authCode: String, codeVerifier: String): SupabaseResult<Session> =
        SupabaseResult
            .Success(
                dummySession.copy(
                    accessToken = "exch-acc",
                    refreshToken = "exch-ref",
                    expiresIn = 1111L,
                ),
            ).also {
                lastExchangeAuthCode = authCode
                lastExchangeCodeVerifier = codeVerifier
            }

    override suspend fun mfaEnroll(factorType: MfaFactorType, friendlyName: String?, issuer: String?, phone: String?, accessToken: String): SupabaseResult<MfaEnrollResponse> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun mfaChallenge(factorId: String, accessToken: String): SupabaseResult<MfaChallengeResponse> =
        SupabaseResult.Success(MfaChallengeResponse(id = "challenge-1", factorId = factorId)).also {
            lastMfaChallengeFactorId = factorId
        }

    override suspend fun mfaVerify(factorId: String, challengeId: String, code: String, accessToken: String): SupabaseResult<MfaVerifyResponse> =
        SupabaseResult
            .Success(
                MfaVerifyResponse(
                    accessToken = "mfa-acc",
                    refreshToken = "mfa-ref",
                    tokenType = "bearer",
                    expiresIn = 3600,
                    user = dummySession.user,
                ),
            ).also {
                lastMfaVerifyChallengeId = challengeId
                lastMfaVerifyCode = code
                lastMfaVerifyAccessToken = accessToken
            }

    override suspend fun mfaUnenroll(factorId: String, accessToken: String): SupabaseResult<MfaUnenrollResponse> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun mfaListFactors(accessToken: String): SupabaseResult<MfaListFactorsResponse> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun mfaGetAuthenticatorAssuranceLevel(accessToken: String): SupabaseResult<AuthenticatorAssuranceLevel> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun passkeyStartRegistration(accessToken: String): SupabaseResult<PasskeyRegistrationOptionsResponse> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun passkeyVerifyRegistration(accessToken: String, challengeId: String, credential: JsonObject): SupabaseResult<PasskeyMetadata> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun passkeyStartAuthentication(captchaToken: String?): SupabaseResult<PasskeyAuthenticationOptionsResponse> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun passkeyVerifyAuthentication(challengeId: String, credential: JsonObject): SupabaseResult<Session> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun passkeyList(accessToken: String): SupabaseResult<List<Passkey>> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun passkeyUpdate(accessToken: String, passkeyId: String, friendlyName: String): SupabaseResult<Passkey> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun passkeyDelete(accessToken: String, passkeyId: String): SupabaseResult<Unit> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun oauthGetAuthorizationDetails(accessToken: String, authorizationId: String): SupabaseResult<OAuthAuthorizationDetails> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun oauthApproveAuthorization(accessToken: String, authorizationId: String): SupabaseResult<OAuthRedirect> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun oauthDenyAuthorization(accessToken: String, authorizationId: String): SupabaseResult<OAuthRedirect> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun oauthListGrants(accessToken: String): SupabaseResult<List<OAuthGrant>> =
        SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun oauthRevokeGrant(accessToken: String, clientId: String): SupabaseResult<Unit> =
        SupabaseResult.Failure(SupabaseError("not used"))
}

private class FakeSessionManager(
    session: Session? = null,
) : SessionManager {
    private val state =
        MutableStateFlow<SessionState>(
            if (session == null) SessionState.NotAuthenticated else SessionState.Authenticated(session),
        )
    override val sessionState: StateFlow<SessionState> = state
    override val currentSession: Session?
        get() = (state.value as? SessionState.Authenticated)?.session
    override val accessToken: String?
        get() = currentSession?.accessToken
    val wasAuthenticated: Boolean
        get() = state.value is SessionState.Authenticated
    var refreshCalled: Boolean = false
    var refreshResult: SupabaseResult<Session> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun saveSession(session: Session) {
        state.value = SessionState.Authenticated(session)
    }

    override suspend fun clearSession() {
        state.value = SessionState.NotAuthenticated
    }

    override suspend fun refreshSession(): SupabaseResult<Session> {
        refreshCalled = true
        return refreshResult
    }

    override suspend fun restoreSession(): SupabaseResult<Session> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun initialize(): SupabaseResult<Session> = restoreSession()

    override fun startAutoRefresh() = Unit

    override fun stopAutoRefresh() = Unit

    override fun dispose() = close()

    override fun close() = Unit
}
