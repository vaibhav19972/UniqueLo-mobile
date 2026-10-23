package com.uniquelo.app

import com.uniquelo.app.data.SnapshotCache
import com.uniquelo.app.data.ThemePersistence
import com.uniquelo.app.state.AppGraph
import com.uniquelo.app.state.CatalogState
import com.uniquelo.app.state.CatalogStore
import com.uniquelo.app.state.RootStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Catalogue state machine: filters/search and the offline-first cache. */
class CatalogStoreTest {

    @Test
    fun filtersByUniverseCategoryAndQuery() {
        val catalog = CatalogStore(
            repository = FakeRepository(snapshot()),
            scope = unconfinedScope,
        )
        catalog.load()
        catalog.selectUniverse("itihaas")

        assertEquals(1, catalog.results.size)
        assertEquals("kurta", catalog.results.first().slug)

        catalog.selectUniverse(null)
        catalog.selectCategory("tops")
        assertEquals("sakura-hoodie", catalog.results.single().slug)

        catalog.selectCategory(null)
        catalog.query = "charcoal"
        assertEquals("kurta", catalog.results.single().slug)

        // A hidden universe filter must not constrain a global search.
        catalog.selectUniverse("itihaas")
        catalog.query = "sakura"
        assertEquals("sakura-hoodie", catalog.results.single().slug)
    }

    @Test
    fun coldStartServesSavedCatalogueWithoutSpinner() {
        val cache = FakeCache(snapshot())
        val root = rootForTest(cache = cache)

        // The root store served the cache during construction — before any
        // load() call.
        val ready = root.catalog.catalogState as CatalogState.Ready
        assertTrue(ready.fromCache)
        assertEquals(2, root.catalog.products.size)
    }

    @Test
    fun offlineColdStartFallsBackToCacheAndReportsStale() {
        val cache = FakeCache(snapshot())
        val repository = FakeRepository(snapshot()).apply { failCount = 99 }
        val catalog = CatalogStore(
            repository = repository,
            cache = cache,
            scope = unconfinedScope,
        )
        catalog.load()

        val ready = catalog.catalogState as CatalogState.Ready
        assertTrue(ready.fromCache)
        assertEquals("Couldn't reach the atelier. Check your connection and try again.", catalog.syncError)

        // Connectivity returns: a retry goes live and clears the stale flag.
        repository.failCount = 0
        catalog.retry()

        val live = catalog.catalogState as CatalogState.Ready
        assertTrue(!live.fromCache)
        assertNull(catalog.syncError)
        // The cache now holds the live data too.
        assertEquals(true, cache.saved != null)
    }
}

/** Test construction site: one shared scope, fakes injected (ADR-8). */
internal fun rootForTest(
    cache: SnapshotCache? = null,
    repository: FakeRepository = FakeRepository(snapshot()),
    bagStorage: FakeBagJournal? = null,
    themeStorage: ThemePersistence? = null,
): RootStore = AppGraph.createStore(
    scope = unconfinedScope,
    repository = repository,
    cache = cache,
    bagStorage = bagStorage,
    themeStorage = themeStorage,
)
