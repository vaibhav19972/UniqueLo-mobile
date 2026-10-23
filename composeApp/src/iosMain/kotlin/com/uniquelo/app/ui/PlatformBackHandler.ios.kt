package com.uniquelo.app.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // The iOS shell is not part of this MVP. When it is added, forward this to
    // the UINavigationController's interactive pop gesture.
}
