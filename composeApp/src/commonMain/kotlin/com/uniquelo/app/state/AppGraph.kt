package com.uniquelo.app.state

import com.uniquelo.app.data.BagPersistence
import com.uniquelo.app.data.SnapshotCache
import com.uniquelo.app.data.SupabaseRepository
import com.uniquelo.app.data.ThemePersistence
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.data.WishlistPersistence
import com.uniquelo.app.data.defaultBagPersistence
import com.uniquelo.app.data.defaultSnapshotCache
import com.uniquelo.app.data.defaultThemePersistence
import com.uniquelo.app.data.defaultWishlistPersistence
import com.uniquelo.app.navigation.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled DI root (ADR-8): the single place where repository, cache, bag
 * storage and the shared store scope are composed into a [RootStore].
 *
 * All stores share ONE scope so [RootStore.close] cancels every in-flight job
 * exactly once — the audit (A6/A7) penalized scopes nobody could reach.
 */
object AppGraph {

    fun createStore(
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        repository: UniqueLoRepository = SupabaseRepository(),
        cache: SnapshotCache? = defaultSnapshotCache(),
        bagStorage: BagPersistence? = defaultBagPersistence(),
        themeStorage: ThemePersistence? = defaultThemePersistence(),
        wishlistStorage: WishlistPersistence? = defaultWishlistPersistence(),
    ): RootStore = RootStore(
        catalog = CatalogStore(repository = repository, scope = scope, cache = cache),
        bag = BagStore(bagStorage = bagStorage),
        checkout = CheckoutStore(repository = repository, scope = scope),
        newsletter = NewsletterStore(repository = repository, scope = scope),
        router = Router(),
        theme = ThemeStore(themeStorage),
        wishlist = WishlistStore(wishlistStorage),
        scope = scope,
    )
}
