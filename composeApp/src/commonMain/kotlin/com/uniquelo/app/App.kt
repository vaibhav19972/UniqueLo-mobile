package com.uniquelo.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.uniquelo.app.data.platformHttpEngine
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

        val scope = rememberCoroutineScope()
        val store = remember(scope) { Store(scope = scope) }

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Bone)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            when {
                store.checkoutOpen -> CheckoutScreen(
                    store = store,
                    onBack = { store.closeCheckout() },
                )

                product != null -> ProductScreen(
                    product = product,
                    store = store,
                    onBack = { store.closeProduct() },
                )

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = store.tab,
                        modifier = Modifier.weight(1f),
                        label = "tab",
                    ) { tab ->
                        when (tab) {
                            Tab.Discover -> DiscoverScreen(
                                store = store,
                                onOpenProduct = { store.open(it) },
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
