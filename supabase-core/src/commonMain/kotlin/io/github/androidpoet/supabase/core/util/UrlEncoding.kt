package io.github.androidpoet.supabase.core.util

private const val HEX_CHARS = "0123456789ABCDEF"

/**
 * Percent-encodes [value] for use as a single URL component (RFC 3986). The
 * unreserved set (`A-Z a-z 0-9 - . _ ~`) passes through unchanged; everything
 * else is encoded from its UTF-8 bytes. Shared so the auth modules don't each
 * carry their own copy.
 */
public fun urlEncode(value: String): String = buildString {
    for (char in value) {
        if (char.isLetterOrDigit() || char in "-._~") {
            append(char)
        } else {
            for (byte in char.toString().encodeToByteArray()) {
                append('%')
                append(HEX_CHARS[(byte.toInt() shr 4) and 0x0F])
                append(HEX_CHARS[byte.toInt() and 0x0F])
            }
        }
    }
}
