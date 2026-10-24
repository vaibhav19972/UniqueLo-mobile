package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.WishlistPersistence

/**
 * Saved product slugs, persisted across process death.
 *
 * Saved slugs are NOT pruned when a product disappears from the catalogue — a
 * saved piece stays saved until the user unsaves it.
 */
class WishlistStore(
    private val storage: WishlistPersistence? = null,
) {
    /** Saved product slugs, in insertion order is NOT required; a Set is fine. */
    var slugs: Set<String> by mutableStateOf(storage?.load().orEmpty())
        private set

    val count: Int get() = slugs.size

    fun contains(slug: String): Boolean = slug in slugs

    /** Adds when absent, removes when present. Returns the new saved state. Persists on every change. */
    fun toggle(slug: String): Boolean {
        if (slug.isBlank()) return false
        val newSaved = !contains(slug)
        slugs = if (newSaved) {
            slugs + slug
        } else {
            slugs - slug
        }
        storage?.save(slugs)
        return newSaved
    }

    fun remove(slug: String) {
        if (slug !in slugs) return
        slugs = slugs - slug
        storage?.save(slugs)
    }
}
