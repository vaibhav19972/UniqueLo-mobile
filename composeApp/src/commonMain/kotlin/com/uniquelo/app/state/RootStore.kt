package com.uniquelo.app.state

import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.navigation.Destination
import com.uniquelo.app.navigation.Router
import com.uniquelo.app.model.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Thin facade owning the feature stores, the shared scope and the [Router]
 * (ADR-4). Feature stores never call each other: cross-store moves happen here.
 *
 * Screens take narrow slices ([CatalogStore], [BagStore], …), never this type.
 */
class RootStore(
    val catalog: CatalogStore,
    val bag: BagStore,
    val checkout: CheckoutStore,
    val newsletter: NewsletterStore,
    val router: Router,
    val theme: ThemeStore,
    val wishlist: WishlistStore,
    val review: ReviewStore,
    val orders: OrdersStore,
    private val scope: CoroutineScope,
) {
    init {
        // Wire bag rehydration BEFORE serving the cache: constructor-time
        // snapshots must reach the bag journal hook.
        catalog.onCatalogUpdated = { snapshot, fromLive ->
            bag.validateAgainst(snapshot, fromLive)
        }
        catalog.warmFromCache()
    }

    val bagCount: Int get() = bag.bagCount
    val subtotalCents: Int get() = bag.subtotalCents
    val bagIsEmpty: Boolean get() = bag.isEmpty

    val savedCount: Int get() = wishlist.count
    fun openSaved() { if (router.current != Destination.Saved) router.push(Destination.Saved) }
    fun toggleSaved(slug: String) = wishlist.toggle(slug)

    val ordersCount: Int get() = orders.count
    fun openOrders() { if (router.current != Destination.Orders) router.push(Destination.Orders) }

    // Navigation ----------------------------------------------------------------

    fun selectTab(tab: Destination) {
        router.selectTab(tab)
    }

    fun openProduct(product: Product) {
        router.push(Destination.Product(slug = product.slug))
    }

    /**
     * System-back entry point. Checkout must go through [closeCheckout] so a
     * submitting order cannot be popped out from under the user, and so the
     * session is abandoned when the user backs out of the confirmation screen.
     * Calling `router.back()` directly here regressed both guarantees.
     */
    fun onBack() {
        if (router.current == Destination.Checkout) {
            closeCheckout()
            return
        }
        router.back()
    }

    // Checkout ------------------------------------------------------------------

    fun requestCheckout() {
        if (router.current == Destination.Checkout) return
        if (!checkout.beginSession(hasLines = !bag.isEmpty)) return
        router.push(Destination.Checkout)
    }

    fun closeCheckout() {
        if (checkout.orderState is OrderState.Submitting) return
        checkout.abandon()
        router.popIfTop(Destination.Checkout)
    }

    fun finishOrder() {
        checkout.finish()
        router.resetToDiscover()
    }

    fun submitOrder(form: CheckoutForm) {
        val lines = bag.takeForOrder()
        val totalCents = bag.subtotalCents
        val itemCount = lines.sumOf { it.quantity }
        checkout.submitOrder(
            form = form,
            lines = lines,
            onOrderCommitted = { order ->
                bag.onOrderCommitted()
                orders.recordOrder(order, totalCents, itemCount)
            },
        )
    }

    fun submitReview(productSlug: String) = review.submit(productSlug)

    /**
     * Releases every store's jobs by cancelling the **shared** scope exactly
     * once. Called by the owning ViewModel from `onCleared`.
     */
    fun close() {
        scope.cancel()
    }
}
