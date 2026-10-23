package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.data.SupabaseRepository
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Variant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    Discover("Discover"),
    Bag("Bag"),
    Profile("Profile"),
}

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val snapshot: CatalogSnapshot) : CatalogState
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
 */
class Store(
    private val repository: UniqueLoRepository = SupabaseRepository(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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

    val lines = mutableStateListOf<CartLine>()

    var checkoutOpen by mutableStateOf(false)
        private set
    var orderState by mutableStateOf<OrderState>(OrderState.Idle)
        private set

    var subscribeEmail by mutableStateOf("")
    var subscribeState by mutableStateOf<SubscribeState>(SubscribeState.Idle)
        private set

    private var loaded = false

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
        catalogState = CatalogState.Loading
        scope.launch {
            catalogState = runCatching { repository.loadCatalog() }
                .fold(
                    onSuccess = { CatalogState.Ready(it) },
                    onFailure = { CatalogState.Error(it.readableMessage()) },
                )
        }
    }

    fun retry() = load(force = true)

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
    }

    fun increment(sku: String) {
        val index = lines.indexOfFirst { it.variant.sku == sku }
        if (index < 0) return
        val line = lines[index]
        val max = if (line.variant.quantity > 0) line.variant.quantity else 10
        if (line.quantity >= max) return
        lines[index] = line.copy(quantity = line.quantity + 1)
    }

    fun decrement(sku: String) {
        val index = lines.indexOfFirst { it.variant.sku == sku }
        if (index < 0) return
        val line = lines[index]
        if (line.quantity <= 1) lines.removeAt(index) else lines[index] = line.copy(quantity = line.quantity - 1)
    }

    fun remove(sku: String) {
        lines.removeAll { it.variant.sku == sku }
    }

    fun requestCheckout() {
        if (lines.isNotEmpty()) {
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
            runCatching { repository.createOrder(form, lines.toList()) }
                .onSuccess { order ->
                    lines.clear()
                    orderState = OrderState.Success(order)
                }
                .onFailure { error ->
                    orderState = OrderState.Error(error.readableMessage())
                }
        }
    }

    fun finishOrder() {
        checkoutOpen = false
        orderState = OrderState.Idle
        tab = Tab.Discover
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
            runCatching { repository.subscribe(clean) }
                .onSuccess {
                    subscribeEmail = ""
                    subscribeState = SubscribeState.Success
                }
                .onFailure { error ->
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
