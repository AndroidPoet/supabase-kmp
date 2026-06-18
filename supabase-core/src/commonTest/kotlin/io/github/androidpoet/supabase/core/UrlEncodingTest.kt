package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.util.urlEncode
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlEncodingTest {
    @Test
    fun test_unreservedAsciiPassesThroughUnchanged() {
        val unreserved = "ABCabc012-._~"
        assertEquals(unreserved, urlEncode(unreserved))
    }

    @Test
    fun test_spaceIsPercentEncoded() {
        assertEquals("a%20b", urlEncode("a b"))
    }

    @Test
    fun test_reservedCharsArePercentEncoded() {
        assertEquals("a%2Fb%3Fc%23d", urlEncode("a/b?c#d"))
    }

    @Test
    fun test_plusIsPercentEncodedNotTreatedAsSpace() {
        assertEquals("a%2Bb", urlEncode("a+b"))
    }

    @Test
    fun test_nonAsciiLetterIsPercentEncodedFromUtf8Bytes() {
        // The previous Char.isLetterOrDigit() check let Unicode letters through raw.
        // é is U+00E9 → UTF-8 0xC3 0xA9.
        assertEquals("caf%C3%A9", urlEncode("café"))
    }

    @Test
    fun test_cjkLetterIsPercentEncoded() {
        // 中 is U+4E2D → UTF-8 0xE4 0xB8 0xAD.
        assertEquals("%E4%B8%AD", urlEncode("中"))
    }

    @Test
    fun test_emptyString() {
        assertEquals("", urlEncode(""))
    }
}
