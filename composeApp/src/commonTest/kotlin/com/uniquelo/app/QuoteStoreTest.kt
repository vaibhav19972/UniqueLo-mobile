package com.uniquelo.app

import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.CouponResult
import com.uniquelo.app.data.QuoteResponse
import com.uniquelo.app.data.SupabaseException
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.state.QuoteState
import com.uniquelo.app.state.QuoteStore
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuoteStoreTest {

    @Test
    fun quoteSucceedsTotalsComeFromResponse() {
        val repo = FakeRepository(snapshot())
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()

        root.bag.add(product, product.variants.first())

        val state = root.quote.state
        assertIs<QuoteState.Ready>(state)
        assertEquals(149900, state.quote.subtotalCents)
        assertEquals(0, state.quote.shippingCents)
        assertEquals(5000, state.quote.codFeeCents)
        assertEquals(154900, state.quote.totalCents)
    }

    @Test
    fun quoteFailsStateIsFailedNoInventedTotal() {
        val repo = FakeRepository(snapshot())
        repo.quoteOrderFailure = SupabaseException("Failed to connect (offline)")
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()

        root.bag.add(product, product.variants.first())

        val state = root.quote.state
        assertIs<QuoteState.Failed>(state)
        assertTrue(state.reason.contains("Couldn't reach the atelier", ignoreCase = true))
        assertNull(root.quote.currentQuote)

        // Retry after connection recovers
        repo.quoteOrderFailure = null
        root.quote.retry()

        assertIs<QuoteState.Ready>(root.quote.state)
    }

    @Test
    fun couponAcceptedDiscountShownAndPayloadContainsCouponCode() {
        val repo = FakeRepository(snapshot())
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()
        root.bag.add(product, product.variants.first())

        root.quote.applyCoupon("DISCOUNT100")

        val state = root.quote.state
        assertIs<QuoteState.Ready>(state)
        assertEquals(10000, state.quote.discountCents)
        assertEquals("DISCOUNT100", state.quote.coupon?.code)
        assertTrue(state.quote.coupon?.isValid == true)

        root.submitOrder(
            CheckoutForm(
                name = "Aria Sen",
                email = "aria@sen.in",
                addressLine = "12 Heritage Lane",
                city = "Jaipur",
                postalCode = "302001",
                paymentMethod = "cod",
                couponCode = "DISCOUNT100",
            ),
        )

        assertEquals("DISCOUNT100", repo.lastSubmittedForm?.couponCode)
    }

    @Test
    fun couponRejectedErrorSurfacedTotalsUnchangedAndStrippedFromPayload() {
        val repo = FakeRepository(snapshot())
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()
        root.bag.add(product, product.variants.first())

        root.quote.applyCoupon("NOPE")

        val state = root.quote.state
        assertIs<QuoteState.Ready>(state)
        assertEquals("This code is not valid.", state.quote.coupon?.error)
        assertEquals(0, state.quote.discountCents)
        assertEquals(154900, state.quote.totalCents)

        // Customer ignores error and submits: payload must have null couponCode so place_order does not fail
        root.submitOrder(
            CheckoutForm(
                name = "Aria Sen",
                email = "aria@sen.in",
                addressLine = "12 Heritage Lane",
                city = "Jaipur",
                postalCode = "302001",
                paymentMethod = "cod",
                couponCode = "NOPE",
            ),
        )

        assertNull(repo.lastSubmittedForm?.couponCode)
    }

    @Test
    fun codFeePresentWhenCodAndZeroWhenPrepaid() {
        val repo = FakeRepository(snapshot())
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()
        root.bag.add(product, product.variants.first())

        root.quote.selectPaymentMethod("cod")
        val codState = root.quote.state
        assertIs<QuoteState.Ready>(codState)
        assertEquals(5000, codState.quote.codFeeCents)

        root.quote.selectPaymentMethod("upi")
        val prepaidState = root.quote.state
        assertIs<QuoteState.Ready>(prepaidState)
        assertEquals(0, prepaidState.quote.codFeeCents)
    }

    @Test
    fun orderCommittedRecordsServerTotalNotSubtotal() {
        val repo = FakeRepository(snapshot())
        // Subtotal is 149900, but server returns total 159800 with 9900 shipping
        repo.orderPricingResult = QuoteResponse(
            subtotalCents = 149900,
            shippingCents = 9900,
            totalCents = 159800,
        )
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()
        root.bag.add(product, product.variants.first())

        root.submitOrder(
            CheckoutForm(
                name = "Aria Sen",
                email = "aria@sen.in",
                addressLine = "12 Heritage Lane",
                city = "Jaipur",
                postalCode = "302001",
            ),
        )

        assertEquals(159800, root.orders.orders.first().totalCents)
        assertNotEquals(root.subtotalCents, root.orders.orders.first().totalCents)
    }

    @Test
    fun fallbackNullPricingRecordsZeroAndNeverClientSubtotal() {
        val repo = FakeRepository(snapshot())
        repo.returnNullPricingInOrder = true
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()
        root.bag.add(product, product.variants.first())

        root.submitOrder(
            CheckoutForm(
                name = "Aria Sen",
                email = "aria@sen.in",
                addressLine = "12 Heritage Lane",
                city = "Jaipur",
                postalCode = "302001",
            ),
        )

        assertEquals(0, root.orders.orders.first().totalCents)
    }

    @Test
    fun staleQuoteResponseDiscardedWhenBagChangedInFlight() {
        val repo = FakeRepository(snapshot())
        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()

        val store = QuoteStore(repo, unconfinedScope, debounceMs = 0L)
        val line1 = CartLine(kurta, kurta.variants[0], 1)
        val line2 = CartLine(hoodie, hoodie.variants[0], 1)

        repo.quoteOrderGate = gate1
        store.onBagChanged(listOf(line1))
        assertEquals(QuoteState.Loading, store.state)

        // User adds another item while quote 1 is in-flight
        repo.quoteOrderGate = gate2
        store.onBagChanged(listOf(line1, line2))

        // First quote returns stale result
        repo.quoteOrderResult = QuoteResponse(totalCents = 111111)
        gate1.complete(Unit)

        // State must NOT be set to the stale result
        assertNotEquals(QuoteState.Ready(QuoteResponse(totalCents = 111111)), store.state)

        // Second quote returns latest result
        repo.quoteOrderResult = QuoteResponse(totalCents = 222222)
        gate2.complete(Unit)

        val finalState = store.state
        assertIs<QuoteState.Ready>(finalState)
        assertEquals(222222, finalState.quote.totalCents)
    }

    @Test
    fun codOverMaxUnitsSurfacesServerMessageVerbatim() {
        val repo = FakeRepository(snapshot())
        repo.quoteOrderFailure = SupabaseException(
            "Cash on delivery is limited to 10 units per order (yours has 11). Please pay online.",
            status = 400,
        )
        val root = rootForTest(repository = repo)
        val product = snapshot().products.first()
        root.bag.add(product, product.variants.first())

        val state = root.quote.state
        assertIs<QuoteState.Failed>(state)
        assertEquals(
            "Cash on delivery is limited to 10 units per order (yours has 11). Please pay online.",
            state.reason,
        )
    }
}
