package com.uniquelo.app.ui

import androidx.compose.runtime.Composable

/**
 * Keeps system-bar/keyboard appearance readable when the user overrides the
 * system theme. [followSystem] is true for `ThemeMode.System`: iOS clears its
 * window override so the OS appearance (and live system changes) can apply.
 */
@Composable
expect fun SystemBarsEffect(
    darkTheme: Boolean,
    followSystem: Boolean,
)
