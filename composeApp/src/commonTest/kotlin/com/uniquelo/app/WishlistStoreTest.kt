package com.uniquelo.app

import com.uniquelo.app.state.WishlistStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WishlistStoreTest {

    @Test
    fun toggleSavesAndUnsavesASlug() {
        val wishlist = WishlistStore()

        assertEquals(0, wishlist.count)
        assertFalse(wishlist.contains("kurta"))
        assertTrue(wishlist.slugs.isEmpty())

        val saved = wishlist.toggle("kurta")
        assertTrue(saved)
        assertEquals(1, wishlist.count)
        assertTrue(wishlist.contains("kurta"))
        assertEquals(setOf("kurta"), wishlist.slugs)

        val unsaved = wishlist.toggle("kurta")
        assertFalse(unsaved)
        assertEquals(0, wishlist.count)
        assertFalse(wishlist.contains("kurta"))
        assertTrue(wishlist.slugs.isEmpty())
    }

    @Test
    fun savedSlugsAreRestoredAtConstruction() {
        val storage = FakeWishlistPersistence(setOf("kurta"))
        val wishlist = WishlistStore(storage)

        assertTrue(wishlist.contains("kurta"))
        assertEquals(1, wishlist.count)
        assertEquals(setOf("kurta"), wishlist.slugs)
    }

    @Test
    fun everyMutationPersists() {
        val storage = FakeWishlistPersistence()
        val wishlist = WishlistStore(storage)

        wishlist.toggle("kurta")
        assertEquals(setOf("kurta"), storage.saved)

        wishlist.toggle("hoodie")
        assertEquals(setOf("kurta", "hoodie"), storage.saved)

        wishlist.remove("kurta")
        assertEquals(setOf("hoodie"), storage.saved)

        wishlist.toggle("hoodie")
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun workWithoutPersistence() {
        val wishlist = WishlistStore(null)

        assertFalse(wishlist.contains("kurta"))
        wishlist.toggle("kurta")
        assertTrue(wishlist.contains("kurta"))
        assertEquals(1, wishlist.count)

        wishlist.remove("kurta")
        assertFalse(wishlist.contains("kurta"))
        assertEquals(0, wishlist.count)
    }

    @Test
    fun blankSlugIsIgnored() {
        val storage = FakeWishlistPersistence()
        val wishlist = WishlistStore(storage)

        val result = wishlist.toggle("")
        assertFalse(result)
        assertEquals(0, wishlist.count)
        assertTrue(wishlist.slugs.isEmpty())
        assertTrue(storage.saved.isEmpty())
        assertEquals(0, storage.saves)

        val spaceResult = wishlist.toggle("   ")
        assertFalse(spaceResult)
        assertEquals(0, wishlist.count)
        assertTrue(wishlist.slugs.isEmpty())
        assertTrue(storage.saved.isEmpty())
        assertEquals(0, storage.saves)
    }
}
