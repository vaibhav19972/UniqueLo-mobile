package com.uniquelo.app

import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.data.SavedLine
import com.uniquelo.app.data.SnapshotCache
import com.uniquelo.app.data.SupabaseException
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.Category
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Review
import com.uniquelo.app.model.Universe
import com.uniquelo.app.model.Variant
import com.uniquelo.app.model.formatCents
import com.uniquelo.app.model.groupIndian
import com.uniquelo.app.state.CatalogState
import com.uniquelo.app.state.OrderState
import com.uniquelo.app.state.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreTest {

    private val kurta = product(
        slug = "kurta",
        name = "Embroidered Charcoal Kurta",
        categorySlug = "knitwear",
        universeSlug = "itihaas",
        price = 149900,
    )

    private val hoodie = product(
        slug = "sakura-hoodie",
        name = "Sakura Ronin Hoodie",
        categorySlug = "tops",
        universeSlug = "animeverse",
        price = 159900,
    )

    @Test
    fun addingSameVariantIncrementsQuantity() {
        val store = Store(repositoryFake)
        val variant = kurta.variants.first()

        store.add(kurta, variant)
        store.add(kurta, variant)

        assertEquals(1, store.lines.size)
        assertEquals(2, store.bagCount)
        assertEquals(149900 * 2, store.subtotalCents)
    }

    @Test
    fun differentSizesBecomeDifferentLines() {
        val store = Store(repositoryFake)
        store.add(kurta, kurta.variants[0])
        store.add(kurta, kurta.variants[1])

        assertEquals(2, store.lines.size)
        assertEquals(2, store.bagCount)
    }

    @Test
    fun soldOutVariantCannotBeAdded() {
        val store = Store(repositoryFake)
        val soldOut = kurta.variants.first().copy(status = "out-of-stock")

        store.add(kurta, soldOut)

        assertTrue(store.isEmpty)
    }

    @Test
    fun decrementAtOneRemovesLine() {
        val store = Store(repositoryFake)
        store.add(kurta, kurta.variants.first())

        store.decrement(kurta.variants.first().sku)

        assertTrue(store.isEmpty)
        assertEquals(0, store.bagCount)
    }

    @Test
    fun filtersByUniverseCategoryAndQuery() {
        val store = Store(
            repository = repositoryFake,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        store.load()
        store.selectUniverse("itihaas")

        assertEquals(1, store.results.size)
        assertEquals("kurta", store.results.first().slug)

        store.selectUniverse(null)
        store.selectCategory("tops")
        assertEquals("sakura-hoodie", store.results.single().slug)

        store.selectCategory(null)
        store.query = "charcoal"
        assertEquals("kurta", store.results.single().slug)

        // A hidden universe filter must not constrain a global search.
        store.selectUniverse("itihaas")
        store.query = "sakura"
        assertEquals("sakura-hoodie", store.results.single().slug)
    }

    @Test
    fun submittingOrderClearsBagAndExposesCode() {
        val store = Store(
            repository = repositoryFake,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        store.add(kurta, kurta.variants.first())

        store.submitOrder(
            CheckoutForm(
                name = "QA",
                email = "qa@uniquelo.app",
                addressLine = "12 Craft Lane",
                city = "Jaipur",
                postalCode = "302001",
            ),
        )

        assertTrue(store.isEmpty)
        val state = store.orderState as OrderState.Success
        assertEquals("UL-TEST", state.order.code)
    }

    @Test
    fun inrFormattingUsesIndianGrouping() {
        assertEquals("₹1,499", formatCents(149900))
        assertEquals("₹10,500", formatCents(1050000))
        assertEquals("₹16.50", formatCents(1650))
        assertEquals("12,34,567", groupIndian(1234567))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Offline-first catalog cache + bag journal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun coldStartServesSavedCatalogueWithoutSpinner() {
        val cache = FakeCache(snapshot())
        val store = Store(
            repository = repositoryFake,
            cache = cache,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        // The cache is served in the constructor — before any load() call.
        val ready = store.catalogState as CatalogState.Ready
        assertTrue(ready.fromCache)
        assertEquals(2, store.products.size)
    }

    @Test
    fun offlineColdStartFallsBackToCacheAndReportsStale() {
        val cache = FakeCache(snapshot())
        val repository = FakeRepository(snapshot()).apply { failCount = 99 }
        val store = Store(
            repository = repository,
            cache = cache,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        store.load()

        val ready = store.catalogState as CatalogState.Ready
        assertTrue(ready.fromCache)
        assertEquals("Couldn't reach the atelier. Check your connection and try again.", store.syncError)

        // Connectivity returns: a retry goes live and clears the stale flag.
        repository.failCount = 0
        store.retry()

        val live = store.catalogState as CatalogState.Ready
        assertTrue(!live.fromCache)
        assertNull(store.syncError)
        // The cache now holds the live data too.
        assertEquals(true, cache.saved != null)
    }

    @Test
    fun bagJournalRehydratesAgainstCachedCatalogue() {
        val cache = FakeCache(snapshot())
        val bag = FakeBagStore(listOf(SavedLine(productSlug = "kurta", sku = "kurta-s", quantity = 3)))
        val store = Store(
            repository = FakeRepository(snapshot()),
            cache = cache,
            bagStorage = bag,
        )

        assertEquals(1, store.lines.size)
        assertEquals("kurta-s", store.lines.first().variant.sku)
        assertEquals(3, store.lines.first().quantity)
    }

    @Test
    fun bagJournalKeepsLinesUntilLiveRefreshConfirmsRemoval() {
        // Journal references a size that only exists in the live catalogue.
        val bag = FakeBagStore(listOf(SavedLine(productSlug = "kurta", sku = "kurta-xxl", quantity = 1)))
        val repository = FakeRepository(snapshot()) // cached catalogue without kurta-xxl

        val store = Store(
            repository = repository,
            cache = FakeCache(snapshot()),
            bagStorage = bag,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        assertTrue(store.isEmpty)           // cache stage: no match yet
        assertEquals(1, bag.saved.size)     // journal still intact

        // Live catalogue now contains kurta-xxl.
        repository.snapshot = enrichedSnapshot()
        store.retry()

        assertEquals(1, store.lines.size)
        assertEquals("kurta-xxl", store.lines.single().variant.sku)
    }

    @Test
    fun liveRefreshClampsQuantityToStockAndDropsUnknownLines() {
        val bag = FakeBagStore(
            listOf(
                SavedLine("kurta", "kurta-s", quantity = 9),   // stock shrinks to 2 live
                SavedLine("ghost", "ghost-s", quantity = 2),   // product removed
            ),
        )
        val repository = FakeRepository(shrunkenSnapshot())

        val store = Store(
            repository = repository,
            cache = FakeCache(snapshot()),
            bagStorage = bag,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        // Cache hydration: ghost dropped, kurta clamped to 2 already via live? No —
        // cache holds stock 10, so 9 units ride through; the clamp below must
        // survive the live pass.
        assertEquals(1, store.lines.size)
        assertEquals(9, store.lines.single().quantity)

        store.load() // live refresh against shrunken stock

        assertEquals(1, store.lines.size)
        assertEquals("kurta-s", store.lines.single().variant.sku)
        assertEquals(2, store.lines.single().quantity) // clamped to live stock
    }

    @Test
    fun bagMutationsPersistToJournal() {
        val bag = FakeBagStore()
        val store = Store(
            repository = repositoryFake,
            bagStorage = bag,
        )

        store.add(kurta, kurta.variants[0])
        store.add(kurta, kurta.variants[0])
        store.add(hoodie, hoodie.variants[0])

        assertEquals(
            listOf(
                SavedLine("kurta", "kurta-s", 2),
                SavedLine("sakura-hoodie", "sakura-hoodie-s", 1),
            ),
            bag.saved,
        )

        store.remove("kurta-s")
        assertEquals(listOf<SavedLine>(SavedLine("sakura-hoodie", "sakura-hoodie-s", 1)), bag.saved)

        store.remove("sakura-hoodie-s")
        assertTrue(bag.saved.isEmpty())
    }

    @Test
    fun orderSuccessClearsPersistedBag() {
        val bag = FakeBagStore()
        val store = Store(
            repository = repositoryFake,
            scope = CoroutineScope(Dispatchers.Unconfined),
            bagStorage = bag,
        )
        store.add(kurta, kurta.variants.first())

        store.submitOrder(
            CheckoutForm(
                name = "QA",
                email = "qa@uniquelo.app",
                addressLine = "12 Craft Lane",
                city = "Jaipur",
                postalCode = "302001",
            ),
        )

        assertTrue(store.isEmpty)
        assertTrue(bag.saved.isEmpty())
        assertTrue(store.orderState is OrderState.Success)
    }

    /**
     * A duplicate SKU in the journal (should not happen, but a corrupt write is
     * possible) must not double-render; the largest quantity wins and stays
     * within stock.
     */
    @Test
    fun duplicateJournalEntriesCollapseToMaxQuantity() {
        val bag = FakeBagStore(
            listOf(
                SavedLine("kurta", "kurta-s", 2),
                SavedLine("kurta", "kurta-s", 5),
            ),
        )
        val store = Store(
            repository = FakeRepository(snapshot()),
            cache = FakeCache(snapshot()),
            bagStorage = bag,
        )

        assertEquals(1, store.lines.size)
        assertEquals(5, store.lines.single().quantity)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fakes
    // ─────────────────────────────────────────────────────────────────────────

    private val repositoryFake = FakeRepository(snapshot())

    private class FakeRepository(
        var snapshot: CatalogSnapshot,
    ) : UniqueLoRepository {
        var failCount = 0

        override suspend fun loadCatalog(): CatalogSnapshot {
            if (failCount > 0) {
                failCount--
                throw SupabaseException("Failed to connect (offline)")
            }
            return snapshot
        }

        override suspend fun createOrder(form: CheckoutForm, lines: List<CartLine>): PlacedOrder =
            PlacedOrder(id = "test", code = "UL-TEST", email = form.email)

        override suspend fun subscribe(email: String) = Unit
    }

    private class FakeCache(
        var saved: CatalogSnapshot?,
    ) : SnapshotCache {
        override fun load(): CatalogSnapshot? = saved
        override fun save(snapshot: CatalogSnapshot) {
            saved = snapshot
        }
        override fun clear() {
            saved = null
        }
    }

    private class FakeBagStore(
        initial: List<SavedLine> = emptyList(),
    ) : com.uniquelo.app.data.BagPersistence {
        val saved = initial.toMutableList()
        override fun load(): List<SavedLine> = saved.toList()
        override fun save(lines: List<SavedLine>) {
            saved.clear()
            saved.addAll(lines)
        }
        override fun clear() = saved.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────────────────

    private fun snapshot(): CatalogSnapshot = catalog(listOf(
        product(
            slug = "kurta",
            name = "Embroidered Charcoal Kurta",
            categorySlug = "knitwear",
            universeSlug = "itihaas",
            price = 149900,
        ),
        product(
            slug = "sakura-hoodie",
            name = "Sakura Ronin Hoodie",
            categorySlug = "tops",
            universeSlug = "animeverse",
            price = 159900,
        ),
    ))

    /** Same as [snapshot] but kurta is now shrunken-stock and hoodie is removed. */
    private fun shrunkenSnapshot(): CatalogSnapshot = catalog(
        products = listOf(
            product(
                slug = "kurta",
                name = "Embroidered Charcoal Kurta",
                categorySlug = "knitwear",
                universeSlug = "itihaas",
                price = 149900,
                stock = 2,
            ),
        ),
        universes = listOf(universe("itihaas")),
    )

    /** Same as [snapshot] with an extra `kurta-xxl` size present. */
    private fun enrichedSnapshot(): CatalogSnapshot = snapshot().let { base ->
        val kurtaXxl = base.products.first { it.slug == "kurta" }.let { k ->
            k.copy(variants = k.variants + variantForProduct(k.slug, skuSuffix = "xxl", size = "XXL"))
        }
        base.copy(products = base.products.map { if (it.slug == "kurta") kurtaXxl else it })
    }

    private fun catalog(
        products: List<Product>,
        universes: List<Universe> = listOf(universe("itihaas"), universe("animeverse")),
    ) = CatalogSnapshot(
        products = products,
        universes = universes,
        categories = listOf(
            Category("knitwear", "Knitwear", "Knitwear", ""),
            Category("tops", "Tops", "Tops", ""),
        ),
        collections = emptyList(),
        motifs = emptyList(),
        reviews = emptyList(),
    )

    private fun universe(slug: String) = Universe(
        slug = slug,
        name = slug.replaceFirstChar { it.uppercase() },
        tagline = "",
        lore = "",
        dropCadence = null,
        theme = com.uniquelo.app.model.ThemeTokens(),
        orderNum = 1,
    )

    private fun product(
        slug: String,
        name: String,
        categorySlug: String,
        universeSlug: String,
        price: Int,
        stock: Int = 10,
    ): Product = Product(
        slug = slug,
        name = name,
        subtitle = "Test piece",
        foreignTagline = null,
        description = "Hand embroidered in a small batch.",
        categorySlug = categorySlug,
        universeSlug = universeSlug,
        collectionSlug = null,
        garmentType = "kurta",
        gender = "unisex",
        motifId = null,
        handcrafted = true,
        craftTechniques = emptyList(),
        edits = emptyList(),
        expressionScore = 4,
        rating = 4.8,
        reviewCount = 7,
        soldCount = "50+",
        tags = listOf("embroidery", "handcrafted"),
        featured = true,
        customizable = false,
        material = listOf("Organic cotton"),
        care = listOf("Cold wash"),
        fit = "Boxy",
        batchNumber = 1,
        batchTotal = 50,
        craftRegion = "Lucknow",
        craftCluster = "Chikankari Guild",
        fabricGsm = "280 GSM",
        variants = listOf(
            variantForProduct(slug, skuSuffix = "s", size = "S", stock = stock),
            variantForProduct(slug, skuSuffix = "m", size = "M", stock = stock),
        ),
        images = emptyList(),
        embroidery = null,
        reviews = emptyList<Review>(),
        universe = null,
        category = null,
        collection = null,
        motif = null,
    )

    private fun variantForProduct(
        productSlug: String,
        skuSuffix: String,
        size: String,
        stock: Int = 10,
    ): Variant = Variant(
        sku = "$productSlug-$skuSuffix",
        productSlug = productSlug,
        color = "Obsidian",
        colorHex = "#121214",
        size = size,
        priceCents = 149900,
        compareAtPriceCents = null,
        status = "in-stock",
        quantity = stock,
    )
}
