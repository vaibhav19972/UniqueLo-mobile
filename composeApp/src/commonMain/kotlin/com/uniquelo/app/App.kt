package com.uniquelo.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import com.uniquelo.app.ui.PlatformBackHandler
import com.uniquelo.app.ui.components.UniqueLoBottomBar
import com.uniquelo.app.ui.screens.BagScreen
import com.uniquelo.app.ui.screens.CheckoutScreen
import com.uniquelo.app.ui.screens.DiscoverScreen
import com.uniquelo.app.ui.screens.ProductScreen
import com.uniquelo.app.ui.screens.ProfileScreen
import com.uniquelo.app.ui.theme.Palette
import com.uniquelo.app.ui.theme.UniqueLoTheme

@Composable
fun App() {
    UniqueLoTheme {
        InstallImageLoader()

        // The root store is owned by the ViewModelStoreOwner (activity on
        // Android, the view controller on iOS), not by the composition:
        // rotating the device keeps state that lives in the stores, and
        // `onCleared` closes the shared scope (docs/CONVENTIONS.md Rule 3).
        val store: RootStore = viewModel { AppStoreViewModel() }.store
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
            when (destination) {
                Destination.Checkout -> saveableStateHolder.SaveableStateProvider("checkout") {
                    CheckoutScreen(
                        checkout = store.checkout,
                        bag = bag,
                        onSubmit = store::submitOrder,
                        onBack = { store.closeCheckout() },
                        onFinish = { store.finishOrder() },
                    )
                }

                is Destination.Product -> {
                    // Shell resolves the slug against the live catalogue; a
                    // refresh that removes the product closes the PDP (same
                    // net effect as the pre-router `selectedProduct = null`).
                    val product = catalog.products.firstOrNull { it.slug == destination.slug }
                    if (product != null) {
                        saveableStateHolder.SaveableStateProvider("pdp/${product.slug}") {
                            ProductScreen(
                                product = product,
                                bag = bag,
                                onBack = { router.pop() },
                                onOpenBag = { router.selectTab(Destination.Bag) },
                            )
                        }
                    } else {
                        LaunchedEffect(destination.slug) { router.pop() }
                    }
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = destination,
                        modifier = Modifier.weight(1f),
                        label = "tab",
                    ) { tab ->
                        // State (scroll, selections, forms) is preserved across
                        // tab switches and device rotation via the saveable
                        // state holder keyed by destination.
                        saveableStateHolder.SaveableStateProvider(tab.label ?: tab.toString()) {
                            when (tab) {
                                Destination.Discover -> DiscoverScreen(
                                    catalog = catalog,
                                    newsletter = store.newsletter,
                                    bagCount = bag.bagCount,
                                    onOpenBag = { router.selectTab(Destination.Bag) },
                                    onOpenProduct = { store.openProduct(it) },
                                    listState = discoverListState,
                                )

                                Destination.Bag -> BagScreen(
                                    bag = bag,
                                    onCheckout = { store.requestCheckout() },
                                    onBrowse = { store.selectTab(Destination.Discover) },
                                )

                                Destination.Profile -> ProfileScreen(
                                    onBrowse = { store.selectTab(Destination.Discover) },
                                )

                                else -> Unit // overlays never reach this branch
                            }
                        }
                    }
                    UniqueLoBottomBar(
                        current = destination,
                        bagCount = bag.bagCount,
                        onSelect = { store.selectTab(it) },
                    )
                }
            }
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
