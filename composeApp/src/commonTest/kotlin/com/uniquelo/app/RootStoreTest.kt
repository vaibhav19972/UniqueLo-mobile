package com.uniquelo.app

import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.SavedLine
import com.uniquelo.app.navigation.Destination
import com.uniquelo.app.state.OrderState
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cross-store orchestration (ADR-4): catalogue→bag rehydration wiring,
 * checkout sessions, order commits. These tests guarded the Phase 1 audit
 * semantics — do not weaken them (CONVENTIONS.md Rule 2).
 */
class RootStoreTest {

    // ── Catalogue → bag wiring ─────────────────────────────────────────────

    @Test
    fun bagJournalRehydratesAgainstCachedCatalogue() {
        val journal = FakeBagJournal(listOf(SavedLine(productSlug = "kurta", sku = "kurta-s", quantity = 3)))
        val root = rootForTest(cache = FakeCache(snapshot()), bagStorage = journal)

        assertEquals(1, root.bag.lines.size)
        assertEquals("kurta-s", root.bag.lines.first().variant.sku)
        assertEquals(3, root.bag.lines.first().quantity)
    }

    @Test
    fun bagJournalKeepsLinesUntilLiveRefreshConfirmsRemoval() {
        // Journal references a size that only exists in the live catalogue.
        val journal = FakeBagJournal(listOf(SavedLine(productSlug = "kurta", sku = "kurta-xxl", quantity = 1)))
        val repository = FakeRepository(snapshot()) // cached catalogue without kurta-xxl

        val root = rootForTest(
            repository = repository,
            cache = FakeCache(snapshot()),
            bagStorage = journal,
        )
        assertTrue(root.bag.isEmpty)        // cache stage: no match yet
        assertEquals(1, journal.saved.size) // journal still intact

        // Live catalogue now contains kurta-xxl.
        repository.snapshot = enrichedSnapshot()
        root.catalog.retry()

        assertEquals(1, root.bag.lines.size)
        assertEquals("kurta-xxl", root.bag.lines.single().variant.sku)
    }

    @Test
    fun cacheStageKeepsUnmatchedJournalEntriesAlongsideMatchedOnes() {
        val journal = FakeBagJournal(
            listOf(
                SavedLine("kurta", "kurta-s", quantity = 2),   // matches the cache
                SavedLine("kurta", "kurta-xxl", quantity = 1), // only lives remotely
            ),
        )
        val root = rootForTest(
            repository = FakeRepository(snapshot()),
            cache = FakeCache(snapshot()),
            bagStorage = journal,
        )

        assertEquals(listOf("kurta-s"), root.bag.lines.map { it.variant.sku })
        assertEquals(setOf("kurta-s", "kurta-xxl"), journal.saved.map { it.sku }.toSet())

        // Editing a visible line must not rewrite the journal without the
        // entry that is still waiting for the live catalogue.
        root.bag.increment("kurta-s")
        assertEquals(setOf("kurta-s", "kurta-xxl"), journal.saved.map { it.sku }.toSet())
        assertEquals(3, journal.saved.first { it.sku == "kurta-s" }.quantity)
    }

    @Test
    fun liveRefreshDropsUnmatchedJournalEntriesFromDisk() {
        val journal = FakeBagJournal(listOf(SavedLine("ghost", "ghost-s", quantity = 1)))
        val root = rootForTest(
            repository = FakeRepository(snapshot()),
            cache = FakeCache(snapshot()),
            bagStorage = journal,
        )

        assertTrue(root.bag.isEmpty)
        assertEquals(1, journal.saved.size) // kept while only the cache has spoken

        root.catalog.load() // live catalogue also lacks the ghost product

        assertTrue(root.bag.isEmpty)
        assertTrue(journal.saved.isEmpty()) // live refresh made the removal final
    }

    @Test
    fun liveRefreshClampsQuantityToStockAndDropsUnknownLines() {
        val journal = FakeBagJournal(
            listOf(
                SavedLine("kurta", "kurta-s", quantity = 9), // stock shrinks to 2 live
                SavedLine("ghost", "ghost-s", quantity = 2), // product removed
            ),
        )
        val repository = FakeRepository(shrunkenSnapshot())

        val root = rootForTest(
            repository = repository,
            cache = FakeCache(snapshot()),
            bagStorage = journal,
        )
        // Cache hydration: ghost dropped; kurta rides through at 9 (cache holds
        // stock 10) — the clamp below must survive the live pass.
        assertEquals(1, root.bag.lines.size)
        assertEquals(9, root.bag.lines.single().quantity)

        root.catalog.load() // live refresh against shrunken stock

        assertEquals(1, root.bag.lines.size)
        assertEquals("kurta-s", root.bag.lines.single().variant.sku)
        assertEquals(2, root.bag.lines.single().quantity) // clamped to live stock
    }

    // ── Checkout sessions + order commits ──────────────────────────────────

    @Test
    fun eachCheckoutGetsAFreshSaveableSession() {
        val root = rootForTest()
        root.bag.add(kurta, kurta.variants.first())

        root.requestCheckout()
        val first = root.checkout.checkoutSession
        assertTrue(first > 0)
        assertEquals(Destination.Checkout, root.router.current)

        root.closeCheckout()
        root.requestCheckout()
        assertEquals(first + 1, root.checkout.checkoutSession)
        // A closed+reopened session really reopens a fresh one.
        assertTrue(root.checkout.checkoutOpen)
        assertEquals(Destination.Checkout, root.router.current)

        // Tapping checkout again while it is already open must not reset the
        // half-typed form for the current session.
        root.requestCheckout()
        assertEquals(first + 1, root.checkout.checkoutSession)
        assertTrue(root.checkout.checkoutOpen)
    }

    /**
     * System back is a shell concern, but it must use the same guarded close as
     * the on-screen back button. Calling `router.back()` directly popped the
     * overlay while leaving `checkoutOpen = true`, making checkout unreachable
     * until process death.
     */
    @Test
    fun systemBackOnCheckoutAbandonsSessionAndReturnsToTab() {
        val root = rootForTest()
        root.bag.add(kurta, kurta.variants.first())
        root.requestCheckout()
        val session = root.checkout.checkoutSession

        root.onBack()

        assertEquals(Destination.Discover, root.router.current)
        assertFalse(root.checkout.checkoutOpen)
        assertEquals(OrderState.Idle, root.checkout.orderState)

        // Reopening starts a fresh session instead of reviving the abandoned one.
        root.requestCheckout()
        assertEquals(session + 1, root.checkout.checkoutSession)
    }

    @Test
    fun systemBackCannotPopSubmittingCheckout() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(snapshot()).apply { orderGate = gate }
        val root = rootForTest(repository = repository)
        root.bag.add(kurta, kurta.variants.first())
        root.requestCheckout()
        root.submitOrder(checkoutForm())

        assertTrue(root.checkout.orderState is OrderState.Submitting)

        root.onBack()

        assertEquals(Destination.Checkout, root.router.current)
        assertTrue(root.checkout.checkoutOpen)
        assertTrue(root.checkout.orderState is OrderState.Submitting)

        gate.complete(Unit)
        assertTrue(root.checkout.orderState is OrderState.Success)
    }

    @Test
    fun systemBackAfterSuccessClosesCheckoutSession() {
        val root = rootForTest()
        root.bag.add(kurta, kurta.variants.first())
        root.requestCheckout()
        root.submitOrder(checkoutForm())
        assertTrue(root.checkout.orderState is OrderState.Success)

        root.onBack()

        assertEquals(Destination.Discover, root.router.current)
        assertFalse(root.checkout.checkoutOpen)
        assertEquals(OrderState.Idle, root.checkout.orderState)
    }

    @Test
    fun requestingCheckoutWithEmptyBagIsRejected() {
        val root = rootForTest()

        root.requestCheckout()

        assertEquals(0, root.checkout.checkoutSession)
        assertEquals(Destination.Discover, root.router.current)
    }

    @Test
    fun submittingOrderClearsBagAndExposesCode() {
        val root = rootForTest()
        root.bag.add(kurta, kurta.variants.first())

        root.submitOrder(
            CheckoutForm(
                name = "QA",
                email = "qa@uniquelo.app",
                addressLine = "12 Craft Lane",
                city = "Jaipur",
                postalCode = "302001",
            ),
        )

        assertTrue(root.bag.isEmpty)
        val state = root.checkout.orderState as OrderState.Success
        assertEquals("UL-TEST", state.order.code)
    }

    @Test
    fun orderSuccessClearsPersistedBag() {
        val journal = FakeBagJournal()
        val root = rootForTest(bagStorage = journal)
        root.bag.add(kurta, kurta.variants.first())

        root.submitOrder(
            CheckoutForm(
                name = "QA",
                email = "qa@uniquelo.app",
                addressLine = "12 Craft Lane",
                city = "Jaipur",
                postalCode = "302001",
            ),
        )

        assertTrue(root.bag.isEmpty)
        assertTrue(journal.saved.isEmpty())
        assertTrue(root.checkout.orderState is OrderState.Success)
    }

    @Test
    fun finishOrderLandsOnDiscover() {
        val root = rootForTest()
        root.bag.add(kurta, kurta.variants.first())
        root.requestCheckout()

        root.finishOrder()

        assertEquals(Destination.Discover, root.router.current)
        assertEquals(OrderState.Idle, root.checkout.orderState)
        // Closing checkout does not invent or destroy bag state.
        assertEquals(1, root.bag.lines.size)
    }

    @Test
    fun failedOrderKeepsBagIntact() {
        val journal = FakeBagJournal()
        val repository = FakeRepository(snapshot()).apply { orderFailures = 1 }
        val root = rootForTest(repository = repository, bagStorage = journal)
        root.bag.add(kurta, kurta.variants.first())

        root.submitOrder(
            CheckoutForm(
                name = "QA",
                email = "qa@uniquelo.app",
                addressLine = "12 Craft Lane",
                city = "Jaipur",
                postalCode = "302001",
            ),
        )

        val state = root.checkout.orderState
        assertTrue(state is OrderState.Error)
        assertEquals("The order book is not accepting reservations right now.", state.message)
        assertEquals(listOf(SavedLine("kurta", "kurta-s", 1)), journal.saved)
        assertEquals(1, root.bag.lines.size)
    }

    @Test
    fun openSavedPushesTheSavedOverlay() {
        val wishlistStorage = FakeWishlistPersistence(setOf("kurta"))
        val root = rootForTest(wishlistStorage = wishlistStorage)

        root.selectTab(Destination.Profile)
        assertEquals(Destination.Profile, root.router.current)
        assertEquals(1, root.savedCount)

        root.openSaved()
        assertEquals(Destination.Saved, root.router.current)
    }

    @Test
    fun backClosesTheSavedOverlay() {
        val wishlistStorage = FakeWishlistPersistence(setOf("kurta"))
        val root = rootForTest(wishlistStorage = wishlistStorage)

        root.selectTab(Destination.Profile)
        root.openSaved()
        assertEquals(Destination.Saved, root.router.current)

        root.onBack()
        assertEquals(Destination.Profile, root.router.current)
    }

    private fun checkoutForm() = CheckoutForm(
        name = "QA",
        email = "qa@uniquelo.app",
        addressLine = "12 Craft Lane",
        city = "Jaipur",
        postalCode = "302001",
    )
}
