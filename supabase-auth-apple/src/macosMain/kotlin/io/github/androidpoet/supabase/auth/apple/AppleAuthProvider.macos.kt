@file:OptIn(ExperimentalForeignApi::class)

package io.github.androidpoet.supabase.auth.apple

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSApplication
import platform.AppKit.NSWindow
import platform.AuthenticationServices.ASPresentationAnchor

internal actual fun keyPresentationAnchor(): ASPresentationAnchor {
    val app = NSApplication.sharedApplication
    // windows.first() throws when the app has no windows (e.g. a menu-bar/agent
    // app). Fall back to an empty window like the iOS sibling does with UIWindow().
    return app.keyWindow ?: app.mainWindow ?: (app.windows.firstOrNull() as? ASPresentationAnchor) ?: NSWindow()
}
