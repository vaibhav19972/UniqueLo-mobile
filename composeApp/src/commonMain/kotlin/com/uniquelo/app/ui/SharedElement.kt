package com.uniquelo.app.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Shared-element scopes are provided once by the app shell. Screens opt in with
 * [productSharedElement]; keeping the scopes in composition locals avoids
 * threading two animation types through every screen signature.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

internal val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Android runs the real shared-element transition. iOS keeps the same
 * navigation and animated cross-fade but skips the shared bounds (D-16:
 * Android-first motion, iOS compile-only shell).
 */
expect val supportsSharedElementTransitions: Boolean

/**
 * Matches a product image on a card with the same image on the PDP. The key is
 * the product slug, so only the tapped product morphs (never the whole grid).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.productSharedElement(key: String): Modifier {
    if (!supportsSharedElementTransitions) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@productSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState("product-image-$key"),
            animatedVisibilityScope = animated,
        )
    }
}
