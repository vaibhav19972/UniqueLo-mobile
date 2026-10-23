package com.uniquelo.app.navigation

/**
 * Type-safe navigation model (ADR-3 in docs/DECISIONS.md). Tab roots and
 * overlays share one sealed hierarchy so the back stack stays a plain list of
 * real values — no route strings, no argument bundles.
 *
 * Screens never receive this type directly for *content*; the shell resolves a
 * [Destination] into data (e.g. a [Product] by slug) and passes the narrow
 * state slice the screen needs. Navigation callbacks only.
 */
sealed interface Destination {

    /** Label for tab roots; `null` for overlays, which never sit in the bar. */
    val label: String?
        get() = null

    data object Discover : Destination {
        override val label: String get() = "Discover"
    }

    data object Bag : Destination {
        override val label: String get() = "Bag"
    }

    data object Profile : Destination {
        override val label: String get() = "Profile"
    }

    /** Product detail overlay. `slug` is resolved into a [com.uniquelo.app.model.Product] by the shell. */
    data class Product(val slug: String) : Destination

    /** Checkout overlay. Session numbering lives in CheckoutStore (audit A8). */
    data object Checkout : Destination

    companion object {
        /** Order matters: it is the display order in the bottom bar. */
        val tabs: List<Destination> = listOf(Discover, Bag, Profile)

        fun isTabRoot(destination: Destination): Boolean =
            destination is Discover || destination is Bag || destination is Profile
    }
}
