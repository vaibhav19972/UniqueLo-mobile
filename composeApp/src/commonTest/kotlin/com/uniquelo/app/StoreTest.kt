package com.uniquelo.app

import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.Category
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Review
import com.uniquelo.app.model.Universe
import com.uniquelo.app.model.Variant
import com.uniquelo.app.model.formatCents
import com.uniquelo.app.model.groupIndian
import com.uniquelo.app.state.OrderState
import com.uniquelo.app.state.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val store = Store(repository = FakeRepository(snapshot()))
        val variant = kurta.variants.first()

        store.add(kurta, variant)
        store.add(kurta, variant)

        assertEquals(1, store.lines.size)
        assertEquals(2, store.bagCount)
        assertEquals(149900 * 2, store.subtotalCents)
    }

    @Test
    fun differentSizesBecomeDifferentLines() {
        val store = Store(repository = FakeRepository(snapshot()))
        store.add(kurta, kurta.variants[0])
        store.add(kurta, kurta.variants[1])

        assertEquals(2, store.lines.size)
        assertEquals(2, store.bagCount)
    }

    @Test
    fun soldOutVariantCannotBeAdded() {
        val store = Store(repository = FakeRepository(snapshot()))
        val soldOut = kurta.variants.first().copy(status = "out-of-stock")

        store.add(kurta, soldOut)

        assertTrue(store.isEmpty)
    }

    @Test
    fun decrementAtOneRemovesLine() {
        val store = Store(repository = FakeRepository(snapshot()))
        store.add(kurta, kurta.variants.first())

        store.decrement(kurta.variants.first().sku)

        assertTrue(store.isEmpty)
        assertEquals(0, store.bagCount)
    }

    @Test
    fun filtersByUniverseCategoryAndQuery() {
        val store = Store(
            repository = FakeRepository(snapshot()),
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
            repository = FakeRepository(snapshot()),
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

    private fun snapshot() = CatalogSnapshot(
        products = listOf(kurta, hoodie),
        universes = listOf(
            Universe("itihaas", "Itihaas", "Threads of a brighter Bharat.", "", null, com.uniquelo.app.model.ThemeTokens(), 1),
            Universe("animeverse", "Animeverse", "Icons. Emotions. Forever.", "", null, com.uniquelo.app.model.ThemeTokens(), 2),
        ),
        categories = listOf(
            Category("knitwear", "Knitwear", "Knitwear", ""),
            Category("tops", "Tops", "Tops", ""),
        ),
        collections = emptyList(),
        motifs = emptyList(),
        reviews = emptyList(),
    )

    private fun product(
        slug: String,
        name: String,
        categorySlug: String,
        universeSlug: String,
        price: Int,
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
            Variant(
                sku = "$slug-s",
                productSlug = slug,
                color = "Obsidian",
                colorHex = "#121214",
                size = "S",
                priceCents = price,
                compareAtPriceCents = null,
                status = "in-stock",
                quantity = 10,
            ),
            Variant(
                sku = "$slug-m",
                productSlug = slug,
                color = "Obsidian",
                colorHex = "#121214",
                size = "M",
                priceCents = price,
                compareAtPriceCents = null,
                status = "in-stock",
                quantity = 10,
            ),
        ),
        images = emptyList(),
        embroidery = null,
        reviews = emptyList<Review>(),
        universe = null,
        category = null,
        collection = null,
        motif = null,
    )
}

private class FakeRepository(
    private val snapshot: CatalogSnapshot,
) : UniqueLoRepository {
    override suspend fun loadCatalog(): CatalogSnapshot = snapshot

    override suspend fun createOrder(form: CheckoutForm, lines: List<CartLine>): PlacedOrder =
        PlacedOrder(id = "test", code = "UL-TEST", email = form.email)

    override suspend fun subscribe(email: String) = Unit
}
