package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.SnapshotCache
import com.uniquelo.app.data.SupabaseRepository
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.data.readableMessage
import com.uniquelo.app.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val snapshot: CatalogSnapshot, val fromCache: Boolean) : CatalogState
    data class Error(val message: String) : CatalogState
}

/**
 * Catalogue state machine: query/filters, the offline-first disk cache and the
 * background revalidation. Product data in, [CatalogSnapshot] out.
 *
 * Bag rehydration is *not* handled here: [RootStore] subscribes to
 * [onCatalogUpdated] and forwards snapshots to [BagStore]. [warmFromCache] is
 * public so the wiring order stays explicit — the root store hooks bag
 * validation first, only then the cache is served (constructor-time callbacks
 * would otherwise fire before any listener exists).
 */
class CatalogStore(
    private val repository: UniqueLoRepository = SupabaseRepository(),
    private val scope: CoroutineScope,
    private val cache: SnapshotCache? = null,
) {
    var query by mutableStateOf("")
    var categorySlug by mutableStateOf<String?>(null)
        private set
    var universeSlug by mutableStateOf<String?>(null)
        private set

    var catalogState by mutableStateOf<CatalogState>(CatalogState.Loading)
        private set

    /**
     * Set when a refresh failed but a previously saved catalogue is still being
     * served; cleared on the next successful refresh.
     */
    var syncError by mutableStateOf<String?>(null)
        private set

    /** Called for both cache-served (fromLive=false) and live snapshots. */
    var onCatalogUpdated: ((snapshot: CatalogSnapshot, fromLive: Boolean) -> Unit)? = null

    private var loaded = false
    private var refreshJob: Job? = null

    /** Serves the last saved catalogue (if any) before any network call. */
    fun warmFromCache() {
        val snapshot = cache?.load() ?: return
        catalogState = CatalogState.Ready(snapshot, fromCache = true)
        onCatalogUpdated?.invoke(snapshot, false)
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

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        if (catalogState !is CatalogState.Ready) warmFromCache()
        refreshJob?.cancel()
        refreshJob = scope.launch { revalidate() }
    }

    fun retry() = load(force = true)

    /** Targeted refresh after a review lands; keeps the current filters. */
    fun refreshAfterReview(productSlug: String) { load(force = true) }

    private suspend fun revalidate() {
        try {
            val live = repository.loadCatalog()
            cache?.save(live)
            catalogState = CatalogState.Ready(live, fromCache = false)
            syncError = null
            onCatalogUpdated?.invoke(live, true)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            when (catalogState) {
                is CatalogState.Ready -> syncError = throwable.readableMessage()
                else -> catalogState = CatalogState.Error(throwable.readableMessage())
            }
        }
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
}
