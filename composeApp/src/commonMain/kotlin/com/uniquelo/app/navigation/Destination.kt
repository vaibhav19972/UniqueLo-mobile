package com.uniquelo.app.navigation

/**
 * Type-safe navigation model (ADR-3 in docs/DECISIONS.md). Tab roots and
 * overlays share one sealed hierarchy so the back stack stays a plain list of
 * real values — no route strings, no argument bundles.
 *
 * Screens never receive this type directly for *content*; the shell resolves a
 * [Destination] into data (e.g. a [com.uniquelo.app.model.Product] by slug) and
 * passes the narrow state slice the screen needs. Navigation callbacks only.
 */
sealed interface Destination {

    /** Label for tab roots; `null` for overlays, which never sit in the bar. */
    val label: String?

    data object Discover : Destination {
        override val label: String get() = "Discover"
    }

    data object Bag : Destination {
        override val label: String get() = "Bag"
    }

    data object Profile : Destination {
        override val label: String get() = "Profile"
    }

    /** Product detail overlay. `slug` is resolved into a Product by the shell. */
    data class Product(val slug: String) : Destination {
        override val label: String? get() = null
    }

    /** Saved pieces overlay. Content is resolved by the shell from WishlistStore. */
    data object Saved : Destination {
        override val label: String? get() = null
    }

    /** Checkout overlay. Session numbering lives in CheckoutStore (audit A8). */
    data object Checkout : Destination {
        override val label: String? get() = null
    }
}

/**
 * Display order of the bottom bar.
 *
 * Deliberately a **top-level** val, not a member of `Destination`'s companion
 * object: on ART, an eager list built inside the interface's companion
 * init-order cycle re-entered the sealed objects' own class-init and captured
 * their *uninitialized* `INSTANCE` fields — producing `(null, Bag, Profile)`
 * for the rest of the process (observed as `NoWhenBranchMatchedException` in
 * `iconFor` on the first launch after install; PROJECT_STATE landmine 8).
 * A top-level val lives in a file facade whose clinit nothing points back
 * into `Destination$*` during, so the instances are fully initialized.
 */
val destinationTabs: List<Destination> =
    listOf(Destination.Discover, Destination.Bag, Destination.Profile)

fun Destination.isTabRoot(): Boolean =
    this is Destination.Discover || this is Destination.Bag || this is Destination.Profile
