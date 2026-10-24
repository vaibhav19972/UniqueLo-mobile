package com.uniquelo.app

import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.SavedLine
import com.uniquelo.app.state.BagStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bag mutations + journal persistence (journal-vs-snapshot semantics live in
 *  RootStoreTest where the catalog wiring exists). */
class BagStoreTest {

    @Test
    fun addingSameVariantIncrementsQuantity() {
        val bag = BagStore()
        val variant = kurta.variants.first()

        bag.add(kurta, variant)
        bag.add(kurta, variant)

        assertEquals(1, bag.lines.size)
        assertEquals(2, bag.bagCount)
        assertEquals(149900 * 2, bag.subtotalCents)
    }

    @Test
    fun differentSizesBecomeDifferentLines() {
        val bag = BagStore()
        bag.add(kurta, kurta.variants[0])
        bag.add(kurta, kurta.variants[1])

        assertEquals(2, bag.lines.size)
        assertEquals(2, bag.bagCount)
    }

    @Test
    fun soldOutVariantCannotBeAdded() {
        val bag = BagStore()
        val soldOut = kurta.variants.first().copy(status = "out-of-stock")

        bag.add(kurta, soldOut)

        assertTrue(bag.isEmpty)
    }

    @Test
    fun decrementAtOneRemovesLine() {
        val bag = BagStore()
        bag.add(kurta, kurta.variants.first())

        bag.decrement(kurta.variants.first().sku)

        assertTrue(bag.isEmpty)
        assertEquals(0, bag.bagCount)
    }

    @Test
    fun bagMutationsPersistToJournal() {
        val journal = FakeBagJournal()
        val bag = BagStore(journal)

        bag.add(kurta, kurta.variants[0])
        bag.add(kurta, kurta.variants[0])
        bag.add(hoodie, hoodie.variants[0])

        assertEquals(
            listOf(
                SavedLine("kurta", "kurta-s", 2),
                SavedLine("sakura-hoodie", "sakura-hoodie-s", 1),
            ),
            journal.saved,
        )

        bag.remove("kurta-s")
        assertEquals(listOf<SavedLine>(SavedLine("sakura-hoodie", "sakura-hoodie-s", 1)), journal.saved)

        bag.remove("sakura-hoodie-s")
        assertTrue(journal.saved.isEmpty())
    }

    /**
     * A duplicate SKU in the journal (should not happen, but a corrupt write is
     * possible) must not double-render; the largest quantity wins and stays
     * within stock.
     */
    @Test
    fun duplicateJournalEntriesCollapseToMaxQuantity() {
        val journal = FakeBagJournal(
            listOf(
                SavedLine("kurta", "kurta-s", 2),
                SavedLine("kurta", "kurta-s", 5),
            ),
        )
        val bag = BagStore(journal)
        bag.validateAgainst(FakeCache(snapshot()).load()!!, fromLive = false)

        assertEquals(1, bag.lines.size)
        assertEquals(5, bag.lines.single().quantity)
    }

    @Test
    fun orderCommitClearsBagAndJournal() {
        val journal = FakeBagJournal()
        val bag = BagStore(journal)
        bag.add(kurta, kurta.variants.first())
        assertTrue(journal.saved.isNotEmpty())

        bag.onOrderCommitted()

        assertTrue(bag.isEmpty)
        assertTrue(journal.saved.isEmpty())
    }

    @Test
    fun soldOutByQuantityIsRefusedEvenWhenStatusSaysInStock() {
        val bag = BagStore()
        val zeroStock = variantFixture("kurta", skuSuffix = "s", size = "S", stock = 0)
        assertEquals("in-stock", zeroStock.status)
        assertEquals(0, zeroStock.quantity)
        assertTrue(!zeroStock.purchasable)

        bag.add(kurta, zeroStock)

        assertTrue(bag.isEmpty)
    }

    @Test
    fun incrementStopsAtTheVariantCeiling() {
        val bag = BagStore()
        val variant = variantFixture("kurta", skuSuffix = "s", size = "S", stock = 2)
        bag.add(kurta, variant)
        assertEquals(1, bag.bagCount)

        bag.increment("kurta-s")
        assertEquals(2, bag.bagCount)

        bag.increment("kurta-s")
        assertEquals(2, bag.bagCount)
        assertEquals(2, bag.maxQuantityFor("kurta-s"))
    }

    @Test
    fun maxQuantityForUnknownSkuIsZero() {
        val bag = BagStore()
        assertEquals(0, bag.maxQuantityFor("unknown-sku"))
    }

    @Test
    fun unreportedStockFallsBackToTen() {
        val journal = FakeBagJournal(listOf(SavedLine("kurta", "kurta-s", 15)))
        val bag = BagStore(journal)
        val variant = variantFixture("kurta", skuSuffix = "s", size = "S", stock = 0)
        val product = kurta.copy(variants = listOf(variant))
        bag.validateAgainst(
            CatalogSnapshot(
                products = listOf(product),
                universes = emptyList(),
                categories = emptyList(),
                collections = emptyList(),
                motifs = emptyList(),
                reviews = emptyList(),
            ),
            fromLive = false,
        )

        assertEquals(10, bag.maxQuantityFor("kurta-s"))
    }
}
