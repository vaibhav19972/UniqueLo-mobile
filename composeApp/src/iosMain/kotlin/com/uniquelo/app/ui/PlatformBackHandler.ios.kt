package com.uniquelo.app.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // The D-17 shell hosts the shared UI directly, without a
    // UINavigationController. The in-app back chrome drives navigation; the
    // interactive swipe-back gesture is intentionally out of scope until a
    // navigation controller owns the stack.
}
