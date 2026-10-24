package com.uniquelo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * iOS has no direct status-bar-icon hook like Android's
 * `WindowInsetsController`. Overriding the key window's interface style makes
 * UIKit pick the matching status-bar and keyboard appearance when the user
 * chooses Light/Dark independently of the system setting.
 *
 * In System mode we set `Unspecified` instead of pinning the current style, so
 * the window keeps following live OS appearance changes.
 */
@Composable
actual fun SystemBarsEffect(
    darkTheme: Boolean,
    followSystem: Boolean,
) {
    SideEffect {
        val style = when {
            followSystem -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            darkTheme -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            else -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        val scene = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull()
        val window = scene?.windows
            ?.filterIsInstance<UIWindow>()
            ?.firstOrNull { it.isKeyWindow() }
        window?.overrideUserInterfaceStyle = style
    }
}
