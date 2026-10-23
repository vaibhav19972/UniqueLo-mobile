package com.uniquelo.app.ui

import androidx.compose.runtime.Composable

/**
 * Keeps the Android status/navigation bar icons readable when the user
 * overrides the system theme. iOS has no equivalent composable hook.
 */
@Composable
expect fun SystemBarsEffect(darkTheme: Boolean)
