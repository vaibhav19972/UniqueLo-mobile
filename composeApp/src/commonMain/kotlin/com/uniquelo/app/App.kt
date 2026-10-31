package com.uniquelo.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.uniquelo.app.data.platformHttpEngine
import com.uniquelo.app.navigation.Destination
import com.uniquelo.app.state.AppStoreViewModel
import com.uniquelo.app.state.RootStore
import com.uniquelo.app.ui.LocalAnimatedVisibilityScope
import com.uniquelo.app.ui.LocalSharedTransitionScope
import com.uniquelo.app.ui.PlatformBackHandler
import com.uniquelo.app.ui.components.UniqueLoBottomBar
import com.uniquelo.app.ui.screens.BagScreen
import com.uniquelo.app.ui.screens.CheckoutScreen
import com.uniquelo.app.ui.screens.DiscoverScreen
import com.uniquelo.app.ui.screens.OrdersScreen
import com.uniquelo.app.ui.screens.ProductScreen
import com.uniquelo.app.ui.screens.ProfileScreen
import com.uniquelo.app.ui.screens.SavedScreen
import com.uniquelo.app.ui.theme.Palette
import com.uniquelo.app.ui.theme.UniqueLoTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App() {
    // The ViewModel is resolved before the theme so a persisted appearance
    // preference is available on the first frame (no light→dark flash).
    val store: RootStore = viewModel { AppStoreViewModel() }.store

    UniqueLoTheme(themeMode = store.theme.mode) {
        InstallImageLoader()

        val catalog = store.catalog
        val bag = store.bag
        val router = store.router

        LaunchedEffect(store) {
            catalog.load()
        }

        val destination = router.current

        // Parity with the pre-router shell (D-3): back is enabled everywhere
        // except the Discover root; screens manage their own guarded closes
        // (e.g. a submitting checkout refuses to be popped by accident).
        PlatformBackHandler(enabled = destination != Destination.Discover) {
            store.onBack()
        }

        val saveableStateHolder = rememberSaveableStateHolder()
        val discoverListState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Bone)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            SharedTransitionLayout {
                val sharedTransition = this
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        val enteringPdp = targetState is Destination.Product
                        val leavingPdp = initialState is Destination.Product
                        if (enteringPdp || leavingPdp) {
                            (
                                slideInHorizontally(tween(320)) { it / 8 } +
                                    fadeIn(tween(220))
                                ).togetherWith(
                                slideOutHorizontally(tween(320)) { -it / 12 } +
                                    fadeOut(tween(160)),
                            )
                        } else {
                            fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                        }
                    },
                    label = "destination",
                ) { target ->
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides sharedTransition,
                        LocalAnimatedVisibilityScope provides this,
                    ) {
                        DestinationHost(
                            target = target,
                            store = store,
                            saveableStateHolder = saveableStateHolder,
                            discoverListState = discoverListState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationHost(
    target: Destination,
    store: RootStore,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    discoverListState: androidx.compose.foundation.lazy.LazyListState,
) {
    val catalog = store.catalog
    val bag = store.bag
    val router = store.router

    when (target) {
        Destination.Checkout -> saveableStateHolder.SaveableStateProvider("checkout") {
            CheckoutScreen(
                checkout = store.checkout,
                bag = bag,
                quote = store.quote,
                onSubmit = store::submitOrder,
                onBack = { store.closeCheckout() },
                onFinish = { store.finishOrder() },
            )
        }

        Destination.Saved -> saveableStateHolder.SaveableStateProvider("saved") {
            val savedProducts = catalog.products.filter { it.slug in store.wishlist.slugs }
            SavedScreen(
                savedProducts = savedProducts,
                onBack = { router.pop() },
                onOpenProduct = { store.openProduct(it) },
                onBrowse = { store.selectTab(Destination.Discover) },
                onToggleSaved = { store.toggleSaved(it.slug) },
            )
        }

        Destination.Orders -> saveableStateHolder.SaveableStateProvider("orders") {
            OrdersScreen(
                ordersStore = store.orders,
                onBack = { router.pop() },
                onBrowse = { store.selectTab(Destination.Discover) },
            )
        }

        is Destination.Product -> {
            // Shell resolves the slug against the live catalogue; a refresh
            // that removes the product closes the PDP (same net effect as the
            // pre-router `selectedProduct = null`).
            val product = catalog.products.firstOrNull { it.slug == target.slug }
            if (product != null) {
                saveableStateHolder.SaveableStateProvider("pdp/${product.slug}") {
                    ProductScreen(
                        product = product,
                        bag = bag,
                        saved = product.slug in store.wishlist.slugs,
                        review = store.review,
                        onSubmitReview = { store.submitReview(product.slug) },
                        onToggleSaved = { store.toggleSaved(product.slug) },
                        onBack = { router.pop() },
                        onOpenBag = { router.selectTab(Destination.Bag) },
                    )
                }
            } else {
                LaunchedEffect(target.slug) { router.pop() }
            }
        }

        else -> Column(modifier = Modifier.fillMaxSize()) {
            // State (scroll, selections, forms) is preserved across tab
            // switches and device rotation via the saveable state holder keyed
            // by destination.
            Box(modifier = Modifier.weight(1f)) {
                saveableStateHolder.SaveableStateProvider(target.label ?: target.toString()) {
                    when (target) {
                        Destination.Discover -> DiscoverScreen(
                            catalog = catalog,
                            newsletter = store.newsletter,
                            bagCount = bag.bagCount,
                            savedSlugs = store.wishlist.slugs,
                            onToggleSaved = { store.toggleSaved(it.slug) },
                            onOpenBag = { router.selectTab(Destination.Bag) },
                            onOpenProduct = { store.openProduct(it) },
                            listState = discoverListState,
                        )

                        Destination.Bag -> BagScreen(
                            bag = bag,
                            quote = store.quote,
                            onCheckout = { store.requestCheckout() },
                            onBrowse = { store.selectTab(Destination.Discover) },
                        )

                        Destination.Profile -> ProfileScreen(
                            theme = store.theme,
                            savedCount = store.savedCount,
                            ordersCount = store.ordersCount,
                            onOpenSaved = { store.openSaved() },
                            onOpenOrders = { store.openOrders() },
                            onBrowse = { store.selectTab(Destination.Discover) },
                        )
                    }
                }
            }
            UniqueLoBottomBar(
                current = target,
                bagCount = bag.bagCount,
                onSelect = { store.selectTab(it) },
            )
        }
    }
}

@Composable
private fun InstallImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = { io.ktor.client.HttpClient(platformHttpEngine()) },
                    ),
                )
            }
            .crossfade(true)
            .build()
    }
}
