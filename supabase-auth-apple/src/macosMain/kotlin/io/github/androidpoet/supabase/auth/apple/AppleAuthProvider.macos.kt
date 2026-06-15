@file:OptIn(ExperimentalForeignApi::class)

package io.github.androidpoet.supabase.auth.apple

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSApplication
import platform.AuthenticationServices.ASPresentationAnchor

internal actual fun keyPresentationAnchor(): ASPresentationAnchor {
    val app = NSApplication.sharedApplication
    return app.keyWindow ?: app.mainWindow ?: app.windows.first() as ASPresentationAnchor
}
