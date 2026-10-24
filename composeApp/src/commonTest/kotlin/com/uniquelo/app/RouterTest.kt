package com.uniquelo.app

import com.uniquelo.app.navigation.Destination
import com.uniquelo.app.navigation.Router
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Router semantics (ADR-3, incl. legacy-shell back parity). */
class RouterTest {

    @Test
    fun startsAtDiscover() {
        val router = Router()

        assertEquals(Destination.Discover, router.current)
        assertFalse(router.canPop)
    }

    @Test
    fun selectTabClearsHistoryToTabRoot() {
        val router = Router()
        router.push(Destination.Product("kurta"))
        router.selectTab(Destination.Bag)

        assertEquals(listOf<Destination>(Destination.Bag), router.stack)
        assertEquals(Destination.Bag, router.current)
    }

    @Test
    fun pushAddsSimilarlyCurrentIsNoOp() {
        val router = Router()
        router.push(Destination.Checkout)
        router.push(Destination.Checkout)

        assertEquals(listOf<Destination>(Destination.Discover, Destination.Checkout), router.stack)
    }

    @Test
    fun productToProductReplacesTopOfStack() {
        val router = Router()
        router.push(Destination.Product("kurta"))
        router.push(Destination.Product("hoodie"))

        assertEquals(
            listOf<Destination>(Destination.Discover, Destination.Product("hoodie")),
            router.stack,
        )
    }

    @Test
    fun backFromOverlayPopsToUnderlyingTab() {
        val router = Router()
        router.selectTab(Destination.Bag)
        router.push(Destination.Product("kurta"))
        router.back()

        assertEquals(Destination.Bag, router.current)
        assertFalse(router.canPop)
    }

    @Test
    fun backFromNonDiscoverTabRootReturnsToDiscover() {
        val router = Router()
        router.selectTab(Destination.Profile)
        router.back()

        assertEquals(Destination.Discover, router.current)
    }

    @Test
    fun backAtDiscoverIsNoOp() {
        val router = Router()
        router.back()

        assertEquals(Destination.Discover, router.current)
    }

    @Test
    fun popIfTopOnlyPopsTheExactDestination() {
        val router = Router()
        router.push(Destination.Checkout)
        router.selectTab(Destination.Discover) // pops nothing by itself; resets

        assertTrue(!router.popIfTop(Destination.Checkout))
        assertEquals(Destination.Discover, router.current)
    }

    @Test
    fun resetToDiscoverClearsEverything() {
        val router = Router()
        router.selectTab(Destination.Bag)
        router.push(Destination.Checkout)
        router.resetToDiscover()

        assertEquals(listOf<Destination>(Destination.Discover), router.stack)
    }

    @Test
    fun selectTabRejectsOverlayDestinations() {
        val router = Router()

        assertFailsWith<IllegalArgumentException> {
            router.selectTab(Destination.Checkout)
        }
        assertFailsWith<IllegalArgumentException> {
            router.selectTab(Destination.Product("kurta"))
        }
    }

    @Test
    fun savedOverlayPushesAndPops() {
        val router = Router()
        router.selectTab(Destination.Profile)
        router.push(Destination.Saved)

        assertEquals(Destination.Saved, router.current)
        assertTrue(router.canPop)

        router.pop()
        assertEquals(Destination.Profile, router.current)
    }

    @Test
    fun savedIsNotATabRoot() {
        val router = Router()

        assertFailsWith<IllegalArgumentException> {
            router.selectTab(Destination.Saved)
        }
    }
}
