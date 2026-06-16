package io.github.androidpoet.supabase.e2e

import io.github.androidpoet.supabase.auth.createAuthClient
import io.github.androidpoet.supabase.client.Supabase
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthE2eTest {
    @Test
    fun test_auth_signUpThenSignIn_returnsSessionForSameUser() =
        runTest {
            val config = localSupabaseConfig()
            val client =
                Supabase.create(
                    projectUrl = config.apiUrl,
                    apiKey = config.anonKey,
                )
            val auth = createAuthClient(client)
            val email = "e2e-${UUID.randomUUID()}@example.com"
            val password = "Sup4base!E2e-${UUID.randomUUID()}"

            val signUp =
                auth
                    .signUpWithEmail(email = email, password = password)
                    .getOrThrow()
            val signIn =
                auth
                    .signInWithEmail(email = email, password = password)
                    .getOrThrow()

            client.close()

            assertTrue(signUp.accessToken.isNotBlank(), "sign-up returned a blank access token")
            assertTrue(signIn.accessToken.isNotBlank(), "sign-in returned a blank access token")
            assertEquals(email, signUp.user.email)
            assertEquals(signUp.user.id, signIn.user.id, "sign-in resolved a different user")
        }
}
