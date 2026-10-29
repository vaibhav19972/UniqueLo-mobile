package com.uniquelo.app

import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.OrderDetails
import com.uniquelo.app.model.OrderItemDetail
import com.uniquelo.app.state.OrderLookupState
import com.uniquelo.app.state.OrdersStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OrdersStoreTest {

    @Test
    fun recordsAndPersistsPlacedOrder() {
        val persistence = FakeOrdersPersistence()
        val repo = FakeRepository(snapshot())
        val store = OrdersStore(repository = repo, scope = unconfinedScope, ordersStorage = persistence)

        assertEquals(0, store.count)

        store.recordOrder(
            placed = PlacedOrder(id = "ord-1", code = "UL-ORD1", email = "collector@uniquelo.com"),
            totalCents = 149900,
            itemCount = 2,
        )

        assertEquals(1, store.count)
        assertEquals(1, persistence.saves)
        assertEquals("ord-1", store.orders.first().id)
        assertEquals("UL-ORD1", store.orders.first().code)
        assertEquals(149900, store.orders.first().totalCents)
        assertEquals(2, store.orders.first().itemCount)
    }

    @Test
    fun lookupsOrderSuccessfully() {
        val repo = FakeRepository(snapshot())
        val store = OrdersStore(repository = repo, scope = unconfinedScope)

        val expected = OrderDetails(
            id = "ord-abc",
            code = "UL-ORDABC",
            customerName = "Zoya Sen",
            customerEmail = "zoya@atelier.in",
            status = "confirmed-cod",
            subtotalCents = 149900,
            discountCents = 0,
            shippingCents = 0,
            totalCents = 149900,
            createdAt = "2026-10-29T12:00:00Z",
            items = listOf(
                OrderItemDetail(
                    id = "item-1",
                    productSlug = "kurta",
                    variantSku = "KURTA-L",
                    productName = "Embroidered Charcoal Kurta",
                    color = "Charcoal",
                    size = "L",
                    quantity = 1,
                    priceCents = 149900,
                ),
            ),
        )
        repo.orderDetailsResult = expected

        store.lookupOrder("ord-abc", "zoya@atelier.in")

        val state = store.lookupState
        assertIs<OrderLookupState.Success>(state)
        assertEquals("UL-ORDABC", state.details.code)
        assertEquals("confirmed-cod", state.details.status)
        assertEquals(1, state.details.items.size)

        store.clearLookup()
        assertIs<OrderLookupState.Idle>(store.lookupState)
    }

    @Test
    fun lookupsOrderNotFoundProducesError() {
        val repo = FakeRepository(snapshot())
        val store = OrdersStore(repository = repo, scope = unconfinedScope)
        repo.orderDetailsResult = null

        store.lookupOrder("missing-id", "someone@test.com")

        val state = store.lookupState
        assertIs<OrderLookupState.Error>(state)
        assertTrue(state.message.contains("not found", ignoreCase = true))
    }

    @Test
    fun rootStoreOrderPlacementRecordsOrderInHistory() {
        val ordersStorage = FakeOrdersPersistence()
        val bagStorage = FakeBagJournal()
        val root = rootForTest(bagStorage = bagStorage, ordersStorage = ordersStorage)

        val product = snapshot().products.first()
        root.bag.add(product, product.variants.first())
        assertEquals(1, root.bagCount)

        root.requestCheckout()
        root.submitOrder(
            CheckoutForm(
                name = "Aria Sen",
                email = "aria@sen.in",
                addressLine = "12 Heritage Lane",
                city = "Jaipur",
                postalCode = "302001",
                phone = "9876543210",
                paymentMethod = "cod",
            ),
        )

        assertEquals(1, root.ordersCount)
        assertEquals(1, ordersStorage.saves)
        assertEquals("aria@sen.in", root.orders.orders.first().email)
    }
}
