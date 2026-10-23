package com.uniquelo.app.state

import androidx.compose.runtime.mutableStateListOf
import com.uniquelo.app.data.BagPersistence
import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.SavedLine
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Variant

/**
 * In-session bag lines plus the persisted journal used for process-death
 * recovery and cross-catalogue rehydration.
 *
 * Journal semantics (originally forged in Phase 1 + audit A3/A4 — do not
 * weaken them):
 *  - pending journal entries only disappear once a *live* refresh confirms
 *    their product/size no longer exists; a cache-stage snapshot keeps them
 *    alongside whatever already matched,
 *  - the persisted file always contains the **visible + pending union**, so
 *    editing one visible line never forgets the rest of the journal,
 *  - an explicit removal also strikes the pending entry (a removal is final),
 *  - duplicate journal SKUs collapse to the largest quantity,
 *  - quantities are clamped to live stock (or 10 when stock is unreported).
 */
class BagStore(
    private val bagStorage: BagPersistence? = null,
) {
    val lines = mutableStateListOf<CartLine>()

    private var pendingSavedLines: List<SavedLine> = bagStorage?.load().orEmpty()

    val bagCount: Int get() = lines.sumOf { it.quantity }
    val subtotalCents: Int get() = lines.sumOf { it.lineTotalCents }
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Rebuilds the bag against [snapshot]; see the class doc for semantics. */
    fun validateAgainst(snapshot: CatalogSnapshot, fromLive: Boolean) {
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
            rebuilt += CartLine(product, variant, saved.quantity.coerceIn(1, maxFor(variant)))
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
        if (line.quantity >= maxFor(line.variant)) return
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

    /** Immutable view for order submission — the bag is not mutated here. */
    fun takeForOrder(): List<CartLine> = lines.toList()

    /**
     * Called by [CheckoutStore] only after the repository confirmed the order:
     * an order that failed keeps the bag exactly as it was.
     */
    fun onOrderCommitted() {
        lines.clear()
        pendingSavedLines = emptyList()
        persistBag()
    }

    private fun maxFor(variant: Variant): Int = if (variant.quantity > 0) variant.quantity else 10
}
