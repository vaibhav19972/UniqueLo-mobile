package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.BagPersistence
import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.data.SavedLine
import com.uniquelo.app.data.SnapshotCache
import com.uniquelo.app.data.SupabaseRepository
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.data.defaultBagPersistence
import com.uniquelo.app.data.defaultSnapshotCache
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Variant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    Discover("Discover"),
    Bag("Bag"),
    Profile("Profile"),
}

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val snapshot: CatalogSnapshot, val fromCache: Boolean) : CatalogState
    data class Error(val message: String) : CatalogState
}

sealed interface OrderState {
    data object Idle : OrderState
    data object Submitting : OrderState
    data class Success(val order: PlacedOrder) : OrderState
    data class Error(val message: String) : OrderState
}

sealed interface SubscribeState {
    data object Idle : SubscribeState
    data object Submitting : SubscribeState
    data object Success : SubscribeState
    data class Error(val message: String) : SubscribeState
}

/**
 * Shared, framework-agnostic state holder for the storefront. Catalogue reads,
 * order writes and newsletter writes all go through [UniqueLoRepository], so
 * the same object can be driven by a fake in tests.
 *
 * The store is offline-first: the last successfully loaded catalogue is cached
 * on disk and painted immediately on launch, while a network refresh runs in
 * the background. The bag journal persists as product slug + SKU + quantity
 * and is rehydrated against the catalogue, dropping lines whose product or
 * size disappeared. Own it from a ViewModel/app-graph scope (never a plain
 * `remember`) so it survives configuration changes and window recreation;
 * process death is covered by the on-disk catalogue cache and bag journal.
 */
class Store(
    private val repository: UniqueLoRepository = SupabaseRepository(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val cache: SnapshotCache? = defaultSnapshotCache(),
    private val bagStorage: BagPersistence? = defaultBagPersistence(),
) {
    var tab by mutableStateOf(Tab.Discover)
    var query by mutableStateOf("")
    var categorySlug by mutableStateOf<String?>(null)
        private set
    var universeSlug by mutableStateOf<String?>(null)
        private set
    var selectedProduct by mutableStateOf<Product?>(null)
    var catalogState by mutableStateOf<CatalogState>(CatalogState.Loading)
        private set

    /**
     * Set when a refresh failed but a previously saved catalogue is still being
     * served; cleared on the next successful refresh.
     */
    var syncError by mutableStateOf<String?>(null)
        private set

    val lines = mutableStateListOf<CartLine>()

    var checkoutOpen by mutableStateOf(false)
        private set
    var orderState by mutableStateOf<OrderState>(OrderState.Idle)
        private set

    /**
     * Increments for every new checkout and keys the saveable checkout form.
     * Rotation keeps the current session (and its half-typed form); finishing
     * or abandoning checkout starts a clean one, so one customer's details do
     * not reappear in the next order.
     */
    var checkoutSession by mutableStateOf(0)
        private set

    var subscribeEmail by mutableStateOf("")
    var subscribeState by mutableStateOf<SubscribeState>(SubscribeState.Idle)
        private set

    private var loaded = false

    private var refreshJob: Job? = null

    private var pendingSavedLines: List<SavedLine> = bagStorage?.load().orEmpty()

    init {
        hydrateFromCache()
    }

    /** Serves the last saved catalogue (if any) before any network call. */
    private fun hydrateFromCache() {
        val snapshot = cache?.load() ?: return
        catalogState = CatalogState.Ready(snapshot, fromCache = true)
        rehydrateBag(snapshot, fromLive = false)
    }

    val snapshot: CatalogSnapshot?
        get() = (catalogState as? CatalogState.Ready)?.snapshot

    val products: List<Product>
        get() = snapshot?.products.orEmpty()

    val universes get() = snapshot?.universes.orEmpty()
    val categories get() = snapshot?.categories.orEmpty()

    val isLoading: Boolean
        get() = catalogState is CatalogState.Loading

    val loadError: String?
        get() = (catalogState as? CatalogState.Error)?.message

    val featured: Product?
        get() = featuredFor(products)

    /**
     * Picks an editorial hero inside the current result set, preferring pieces
     * with photography, then the most-reviewed/shortest-named option.
     */
    fun featuredFor(candidates: List<Product>): Product? = candidates
        .filter { it.featured && it.primaryImageUrl != null }
        .sortedWith(compareByDescending<Product> { it.reviewCount }.thenBy { it.name.length })
        .firstOrNull()
        ?: candidates.firstOrNull { it.primaryImageUrl != null }
        ?: candidates.firstOrNull()

    val results: List<Product>
        get() = if (query.isBlank()) {
            filtered(query, categorySlug, universeSlug)
        } else {
            // Search is global: the universe/category cards are hidden while
            // typing, so applying an invisible filter would be confusing.
            filtered(query, categorySlug = null, universeSlug = null)
        }

    val bagCount: Int get() = lines.sumOf { it.quantity }
    val subtotalCents: Int get() = lines.sumOf { it.lineTotalCents }
    val isEmpty: Boolean get() = lines.isEmpty()

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        if (catalogState !is CatalogState.Ready) hydrateFromCache()
        refreshJob?.cancel()
        refreshJob = scope.launch { revalidate() }
    }

    fun retry() = load(force = true)

    private suspend fun revalidate() {
        try {
            val live = repository.loadCatalog()
            cache?.save(live)
            catalogState = CatalogState.Ready(live, fromCache = false)
            syncError = null
            rehydrateBag(live, fromLive = true)
            // Keep an open product page in sync with the refreshed catalogue.
            selectedProduct?.let { current ->
                selectedProduct = live.products.firstOrNull { it.slug == current.slug }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            when (catalogState) {
                is CatalogState.Ready -> syncError = throwable.readableMessage()
                else -> catalogState = CatalogState.Error(throwable.readableMessage())
            }
        }
    }

    /**
     * Rebuilds the bag against [snapshot], clamping quantities to stock and
     * dropping lines whose product or size no longer exists. The bag journal
     * ranks below live in-session lines, so a refresh never shuffles what the
     * user is doing right now.
     *
     * With [fromLive] = false (cache-served snapshot) journal entries that do
     * not match yet are kept and retried against the live catalogue. Only a
     * live refresh is final: whatever still does not match is dropped for good
     * and the disk journal is rewritten to match.
     */
    private fun rehydrateBag(snapshot: CatalogSnapshot, fromLive: Boolean) {
        if (pendingSavedLines.isEmpty() && lines.isEmpty()) return

        val requested = LinkedHashMap<String, SavedLine>()
        // Corrupt journals can carry duplicate SKUs — the largest quantity wins.
        pendingSavedLines.forEach { saved ->
            val existing = requested[saved.sku]
            if (existing == null || saved.quantity > existing.quantity) requested[saved.sku] = saved
        }
        val journalSkus = requested.keys.toSet()
        // In-session lines outrank the journal; the journal matters only at
        // cold start before the user touches anything.
        lines.forEach { line ->
            requested[line.variant.sku] = SavedLine(line.product.slug, line.variant.sku, line.quantity)
        }

        val rebuilt = mutableListOf<CartLine>()
        val matched = mutableSetOf<String>()
        requested.values.forEach { saved ->
            val product = snapshot.products.firstOrNull { it.slug == saved.productSlug } ?: return@forEach
            val variant = product.variants.firstOrNull { it.sku == saved.sku } ?: return@forEach
            if (!variant.inStock) return@forEach
            val cap = if (variant.quantity > 0) variant.quantity else 10
            rebuilt += CartLine(product, variant, saved.quantity.coerceIn(1, cap))
            matched += saved.sku
        }

        pendingSavedLines = if (fromLive) {
            emptyList()
        } else {
            // Keep only journal entries that have not resolved yet. Matched
            // entries now live in `lines`; in-session entries that were never
            // in the journal are not carried into it.
            requested.values.filter { it.sku !in matched && it.sku in journalSkus }
        }

        if (rebuilt != lines.toList()) {
            lines.clear()
            lines.addAll(rebuilt)
        }
        // Persist on every pass: this keeps unmatched cache-stage entries on
        // disk and, after a live refresh, drops the ones that are really gone.
        persistBag()
    }

    /**
     * Writes the visible bag plus any journal entries still waiting for the
     * live catalogue to validate them. Without the second part, editing a
     * visible line before the refresh lands would silently forget the rest of
     * the journal.
     */
    private fun persistBag() {
        val visible = lines.map { SavedLine(it.product.slug, it.variant.sku, it.quantity) }
        val visibleSkus = visible.mapTo(HashSet()) { it.sku }
        val carried = pendingSavedLines
            .filterNot { it.sku in visibleSkus }
            .distinctBy { it.sku }
        bagStorage?.save(visible + carried)
    }

    fun selectTab(next: Tab) {
        tab = next
    }

    fun selectCategory(slug: String?) {
        categorySlug = slug
    }

    fun selectUniverse(slug: String?) {
        universeSlug = if (universeSlug == slug) null else slug
    }

    fun resetFilters() {
        query = ""
        categorySlug = null
        universeSlug = null
    }

    fun open(product: Product) {
        selectedProduct = product
    }

    fun closeProduct() {
        selectedProduct = null
    }

    fun add(product: Product, variant: Variant) {
        if (!variant.inStock) return
        val index = lines.indexOfFirst { it.variant.sku == variant.sku }
        if (index >= 0) {
            increment(variant.sku)
            return
        }
        lines.add(CartLine(product = product, variant = variant, quantity = 1))
        persistBag()
    }

    fun increment(sku: String) {
        val index = lines.indexOfFirst { it.variant.sku == sku }
        if (index < 0) return
        val line = lines[index]
        val max = if (line.variant.quantity > 0) line.variant.quantity else 10
        if (line.quantity >= max) return
        lines[index] = line.copy(quantity = line.quantity + 1)
        persistBag()
    }

    fun decrement(sku: String) {
        val index = lines.indexOfFirst { it.variant.sku == sku }
        if (index < 0) return
        val line = lines[index]
        if (line.quantity <= 1) {
            lines.removeAt(index)
            pendingSavedLines = pendingSavedLines.filterNot { it.sku == sku }
        } else {
            lines[index] = line.copy(quantity = line.quantity - 1)
        }
        persistBag()
    }

    fun remove(sku: String) {
        lines.removeAll { it.variant.sku == sku }
        // An explicit removal must not be undone by a journal entry that was
        // still waiting for the live catalogue.
        pendingSavedLines = pendingSavedLines.filterNot { it.sku == sku }
        persistBag()
    }

    fun requestCheckout() {
        if (checkoutOpen) return
        if (lines.isNotEmpty()) {
            checkoutSession += 1
            orderState = OrderState.Idle
            checkoutOpen = true
        }
    }

    fun closeCheckout() {
        if (orderState is OrderState.Submitting) return
        checkoutOpen = false
        orderState = OrderState.Idle
    }

    fun submitOrder(form: CheckoutForm) {
        if (orderState is OrderState.Submitting) return
        orderState = OrderState.Submitting
        scope.launch {
            try {
                val order = repository.createOrder(form, lines.toList())
                lines.clear()
                pendingSavedLines = emptyList()
                persistBag()
                orderState = OrderState.Success(order)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                orderState = OrderState.Error(error.readableMessage())
            }
        }
    }

    fun finishOrder() {
        checkoutOpen = false
        orderState = OrderState.Idle
        tab = Tab.Discover
    }

    /**
     * Releases the store's coroutine scope. The owning ViewModel calls this
     * from `onCleared` so an in-flight refresh cannot outlive the screen.
     */
    fun close() {
        refreshJob?.cancel()
        scope.cancel()
    }

    fun clearSubscribeError() {
        if (subscribeState is SubscribeState.Error) subscribeState = SubscribeState.Idle
    }

    fun subscribe(email: String = subscribeEmail) {
        if (subscribeState is SubscribeState.Submitting) return
        val clean = email.trim()
        if (!EMAIL.matches(clean)) {
            subscribeState = SubscribeState.Error("Enter a valid email address.")
            return
        }
        subscribeState = SubscribeState.Submitting
        scope.launch {
            try {
                repository.subscribe(clean)
                subscribeEmail = ""
                subscribeState = SubscribeState.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                val message = error.message.orEmpty()
                subscribeState = SubscribeState.Error(
                    if (message.contains("duplicate", ignoreCase = true) ||
                        message.contains("unique", ignoreCase = true)
                    ) {
                        "You are already on the list."
                    } else {
                        "Couldn't subscribe right now. Please try again."
                    },
                )
            }
        }
    }

    fun filtered(
        query: String,
        categorySlug: String?,
        universeSlug: String?,
    ): List<Product> {
        val needle = query.trim()
        return products.filter { product ->
            val inUniverse = universeSlug == null || product.universeSlug == universeSlug
            val inCategory = categorySlug == null || product.categorySlug == categorySlug
            val matches = needle.isEmpty() || listOfNotNull(
                product.name,
                product.subtitle,
                product.description,
                product.universeName,
                product.categoryLabel,
                product.craftRegion,
                product.craftCluster,
                product.tags.joinToString(" "),
            ).any { it.contains(needle, ignoreCase = true) }
            inUniverse && inCategory && matches
        }
    }

    private fun Throwable.readableMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("Failed to connect", ignoreCase = true) ||
                raw.contains("timeout", ignoreCase = true) ||
                raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("ConnectException", ignoreCase = true) ->
                "Couldn't reach the atelier. Check your connection and try again."

            raw.contains("row-level security", ignoreCase = true) ->
                "The order book is not accepting reservations right now."

            raw.isBlank() -> "Something went wrong. Please try again."
            else -> raw.take(180)
        }
    }

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
