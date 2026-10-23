package com.uniquelo.app.ui

import androidx.compose.runtime.Composable

/**
 * Handles the platform back gesture. Android maps this to the system back
 * dispatcher; iOS currently leaves it to the host navigation controller.
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
