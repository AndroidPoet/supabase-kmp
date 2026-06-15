@file:OptIn(ExperimentalForeignApi::class)

package io.github.androidpoet.supabase.auth.apple

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AuthenticationServices.ASPresentationAnchor
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

internal actual fun keyPresentationAnchor(): ASPresentationAnchor =
    UIApplication.sharedApplication.keyWindow
        ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
        ?: UIWindow()
