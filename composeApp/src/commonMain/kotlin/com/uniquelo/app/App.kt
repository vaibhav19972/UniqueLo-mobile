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
import com.uniquelo.app.state.AppStoreViewModel
import com.uniquelo.app.state.Store
import com.uniquelo.app.state.Tab
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

        // The store is owned by the ViewModelStoreOwner (activity on Android,
        // the view controller on iOS), not by the composition: rotating the
        // device or recreating the window keeps the catalogue, bag and
        // selected product intact.
        val store: Store = viewModel { AppStoreViewModel() }.store

        LaunchedEffect(store) {
            store.load()
        }

        val product = store.selectedProduct

        PlatformBackHandler(
            enabled = store.checkoutOpen || product != null || store.tab != Tab.Discover,
        ) {
            when {
                store.checkoutOpen -> store.closeCheckout()
                product != null -> store.closeProduct()
                else -> store.selectTab(Tab.Discover)
            }
        }

        val saveableStateHolder = rememberSaveableStateHolder()
        val discoverListState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Bone)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            when {
                store.checkoutOpen -> saveableStateHolder.SaveableStateProvider("checkout") {
                    CheckoutScreen(
                        store = store,
                        session = store.checkoutSession,
                        onBack = { store.closeCheckout() },
                    )
                }

                product != null -> saveableStateHolder.SaveableStateProvider("pdp/${product.slug}") {
                    ProductScreen(
                        product = product,
                        store = store,
                        onBack = { store.closeProduct() },
                    )
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = store.tab,
                        modifier = Modifier.weight(1f),
                        label = "tab",
                    ) { tab ->
                        // State (scroll, PDP selections, checkout form) is
                        // preserved across tab switches and device rotation
                        // via the saveable state holder keyed by destination.
                        saveableStateHolder.SaveableStateProvider(tab.name) {
                            when (tab) {
                                Tab.Discover -> DiscoverScreen(
                                    store = store,
                                    onOpenProduct = { store.open(it) },
                                    listState = discoverListState,
                                )

                                Tab.Bag -> BagScreen(
                                    store = store,
                                    onBrowse = { store.selectTab(Tab.Discover) },
                                )

                                Tab.Profile -> ProfileScreen(
                                    onBrowse = { store.selectTab(Tab.Discover) },
                                )
                            }
                        }
                    }
                    UniqueLoBottomBar(store)
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
