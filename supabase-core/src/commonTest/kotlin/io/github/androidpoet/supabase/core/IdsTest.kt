package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.types.BucketId
import io.github.androidpoet.supabase.core.types.ChannelId
import io.github.androidpoet.supabase.core.types.ProjectUrl
import io.github.androidpoet.supabase.core.types.SessionId
import io.github.androidpoet.supabase.core.types.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class IdsTest {

    // ── Value class identity ─────────────────────────────────────────

    @Test
    fun test_userId_wrapsString() {
        val id = UserId("usr_abc123")
        assertEquals("usr_abc123", id.value)
    }

    @Test
    fun test_sessionId_wrapsString() {
        val id = SessionId("sess_xyz")
        assertEquals("sess_xyz", id.value)
    }

    @Test
    fun test_bucketId_wrapsString() {
        val id = BucketId("avatars")
        assertEquals("avatars", id.value)
    }

    @Test
    fun test_channelId_wrapsString() {
        val id = ChannelId("room:42")
        assertEquals("room:42", id.value)
    }

    // ── ProjectUrl derived endpoints ─────────────────────────────────

    @Test
    fun test_projectUrl_restUrl() {
        val url = ProjectUrl("https://xyzcompany.supabase.co")
        assertEquals("https://xyzcompany.supabase.co/rest/v1", url.restUrl)
    }

    @Test
    fun test_projectUrl_authUrl() {
        val url = ProjectUrl("https://xyzcompany.supabase.co")
        assertEquals("https://xyzcompany.supabase.co/auth/v1", url.authUrl)
    }

    @Test
    fun test_projectUrl_storageUrl() {
        val url = ProjectUrl("https://xyzcompany.supabase.co")
        assertEquals("https://xyzcompany.supabase.co/storage/v1", url.storageUrl)
    }

    @Test
    fun test_projectUrl_realtimeUrl() {
        val url = ProjectUrl("https://xyzcompany.supabase.co")
        assertEquals("https://xyzcompany.supabase.co/realtime/v1", url.realtimeUrl)
    }

    @Test
    fun test_projectUrl_functionsUrl() {
        val url = ProjectUrl("https://xyzcompany.supabase.co")
        assertEquals("https://xyzcompany.supabase.co/functions/v1", url.functionsUrl)
    }

    @Test
    fun test_projectUrl_stripsNoTrailingSlash() {
        val url = ProjectUrl("https://example.supabase.co")
        // Verify no double-slash between base and path
        assertEquals("https://example.supabase.co/rest/v1", url.restUrl)
    }
}
