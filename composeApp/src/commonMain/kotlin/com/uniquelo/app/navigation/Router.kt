package com.uniquelo.app.navigation

import androidx.compose.runtime.mutableStateListOf

/**
 * Hand-rolled typed back stack (ADR-3). Observable from Compose via the
 * snapshot list; lives inside [com.uniquelo.app.state.RootStore] so navigation
 * and state changes happen in one place on one thread.
 *
 * Parity with the pre-Phase-2 shell (recorded in D-3):
 *  - back on a non-Discover tab root returns to Discover,
 *  - back from a product/checkout overlay pops that overlay,
 *  - [Destination.Checkout] and product overlays are full-screen: the bottom
 *    bar is only visible on tab roots.
 */
class Router(initial: Destination = Destination.Discover) {

    private val backStack = mutableStateListOf(initial)

    val current: Destination
        get() = backStack.last()

    /** Snapshot for tests/debug; the live list is never exposed. */
    val stack: List<Destination>
        get() = backStack.toList()

    val canPop: Boolean
        get() = backStack.size > 1

    /** Switches to a tab root, resetting history. Tab roots never stack. */
    fun selectTab(tab: Destination) {
        require(Destination.isTabRoot(tab)) { "$tab is not a tab root" }
        if (backStack.size == 1 && backStack.first() == tab) return
        backStack.clear()
        backStack.add(tab)
    }

    /** Opens a new screen on top of the current one. Re-opening the current
     *  screen is a no-op; heading to another PDP replaces the current one. */
    fun push(destination: Destination) {
        when {
            current == destination -> Unit
            current is Destination.Product && destination is Destination.Product ->
                backStack[backStack.lastIndex] = destination

            else -> backStack.add(destination)
        }
    }

    /** Pops the top overlay. Returns whether anything was popped. */
    fun pop(): Boolean {
        if (!canPop) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Removes the topmost occurrence of [destination] if it sits on top. */
    fun popIfTop(destination: Destination): Boolean =
        if (current == destination) pop() else false

    /**
     * System-back semantics. Only shell-level callers use this: screens get
     * explicit closures ([popIfTop] via RootStore) so a submitting checkout
     * cannot be popped by accident.
     */
    fun back() {
        if (!pop()) selectTab(Destination.Discover)
    }

    /** Clears everything and lands on [Discover] — used after order placement. */
    fun resetToDiscover() {
        if (backStack.size == 1 && backStack.first() == Destination.Discover) return
        backStack.clear()
        backStack.add(Destination.Discover)
    }
}
