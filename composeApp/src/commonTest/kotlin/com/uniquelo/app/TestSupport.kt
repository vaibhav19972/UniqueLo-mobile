package com.uniquelo.app

import com.uniquelo.app.data.BagPersistence
import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.data.SavedLine
import com.uniquelo.app.data.SnapshotCache
import com.uniquelo.app.data.SupabaseException
import com.uniquelo.app.data.ThemePersistence
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.Category
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Review
import com.uniquelo.app.model.ThemeMode
import com.uniquelo.app.model.ThemeTokens
import com.uniquelo.app.model.Universe
import com.uniquelo.app.model.Variant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Shared fakes and fixtures for the store tests (CONVENTIONS.md Rule 2). */

internal val unconfinedScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

internal class FakeRepository(
    var snapshot: CatalogSnapshot,
) : UniqueLoRepository {
    var failCount = 0
    var orderFailures = 0

    /** When set, [createOrder] suspends until the test releases it. */
    var orderGate: CompletableDeferred<Unit>? = null

    override suspend fun loadCatalog(): CatalogSnapshot {
        if (failCount > 0) {
            failCount--
            throw SupabaseException("Failed to connect (offline)")
        }
        return snapshot
    }

    override suspend fun createOrder(form: CheckoutForm, lines: List<CartLine>): PlacedOrder {
        orderGate?.await()
        if (orderFailures > 0) {
            orderFailures--
            throw SupabaseException("row-level security blocked order insert")
        }
        return PlacedOrder(id = "test", code = "UL-TEST", email = form.email)
    }

    override suspend fun subscribe(email: String) = Unit
}

internal class FakeCache(
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

internal class FakeBagJournal(
    initial: List<SavedLine> = emptyList(),
) : BagPersistence {
    val saved = initial.toMutableList()
    override fun load(): List<SavedLine> = saved.toList()
    override fun save(lines: List<SavedLine>) {
        saved.clear()
        saved.addAll(lines)
    }
    override fun clear() = saved.clear()
}

internal class FakeThemePersistence(
    var stored: ThemeMode? = null,
) : ThemePersistence {
    var saves = 0
    override fun load(): ThemeMode? = stored
    override fun save(mode: ThemeMode) {
        stored = mode
        saves++
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures — must stay byte-identical to the pre-split ones; tests assert
// against layout/stock/price details (e.g. live-refresh clamping).
// ─────────────────────────────────────────────────────────────────────────────

internal fun universe(slug: String) = Universe(
    slug = slug,
    name = slug.replaceFirstChar { it.uppercase() },
    tagline = "",
    lore = "",
    dropCadence = null,
    theme = ThemeTokens(),
    orderNum = 1,
)

internal fun productFixture(
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
        variantFixture(slug, skuSuffix = "s", size = "S", stock = stock),
        variantFixture(slug, skuSuffix = "m", size = "M", stock = stock),
    ),
    images = emptyList(),
    embroidery = null,
    reviews = emptyList<Review>(),
    universe = null,
    category = null,
    collection = null,
    motif = null,
)

internal fun variantFixture(
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

internal val kurta = productFixture(
    slug = "kurta",
    name = "Embroidered Charcoal Kurta",
    categorySlug = "knitwear",
    universeSlug = "itihaas",
    price = 149900,
)

internal val hoodie = productFixture(
    slug = "sakura-hoodie",
    name = "Sakura Ronin Hoodie",
    categorySlug = "tops",
    universeSlug = "animeverse",
    price = 159900,
)

internal fun snapshot(): CatalogSnapshot = catalogFixture(
    listOf(
        productFixture(
            slug = "kurta",
            name = "Embroidered Charcoal Kurta",
            categorySlug = "knitwear",
            universeSlug = "itihaas",
            price = 149900,
        ),
        productFixture(
            slug = "sakura-hoodie",
            name = "Sakura Ronin Hoodie",
            categorySlug = "tops",
            universeSlug = "animeverse",
            price = 159900,
        ),
    ),
)

/** Same as [snapshot] but kurta is now shrunken-stock and hoodie is removed. */
internal fun shrunkenSnapshot(): CatalogSnapshot = catalogFixture(
    products = listOf(
        productFixture(
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
internal fun enrichedSnapshot(): CatalogSnapshot = snapshot().let { base ->
    val kurtaXxl = base.products.first { it.slug == "kurta" }.let { k ->
        k.copy(variants = k.variants + variantFixture(k.slug, skuSuffix = "xxl", size = "XXL"))
    }
    base.copy(products = base.products.map { if (it.slug == "kurta") kurtaXxl else it })
}

internal fun catalogFixture(
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
