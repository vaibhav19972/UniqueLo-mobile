package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.data.readableMessage
import com.uniquelo.app.model.CartLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface OrderState {
    data object Idle : OrderState
    data object Submitting : OrderState
    data class Success(val order: PlacedOrder) : OrderState
    data class Error(val message: String) : OrderState
}

/**
 * Checkout sessions and order submission. The bag is never touched directly:
 * [RootStore] hands in the lines at submission time and clears them through a
 * commit callback only after the repository confirmed the order.
 */
class CheckoutStore(
    private val repository: UniqueLoRepository,
    private val scope: CoroutineScope,
    val quote: QuoteStore? = null,
) {
    var checkoutOpen by mutableStateOf(false)
        private set
    var orderState by mutableStateOf<OrderState>(OrderState.Idle)
        private set

    /**
     * Increments for every new checkout and keys the saveable checkout form.
     * Rotation keeps the current session (and its half-typed form); finishing
     * or abandoning checkout starts a clean one, so one customer's details do
     * not reappear in the next order (audit A8).
     */
    var checkoutSession by mutableStateOf(0)
        private set

    /**
     * Opens a new checkout. Re-requests while a session is already open are a
     * no-op so the half-typed form stays put. Returns whether a session was
     * actually started (false when [hasLines] is false).
     */
    fun beginSession(hasLines: Boolean): Boolean {
        if (checkoutOpen) return false
        if (!hasLines) return false
        checkoutSession += 1
        orderState = OrderState.Idle
        checkoutOpen = true
        return true
    }

    fun abandon() {
        if (orderState is OrderState.Submitting) return
        checkoutOpen = false
        orderState = OrderState.Idle
    }

    fun finish() {
        checkoutOpen = false
        orderState = OrderState.Idle
    }

    /**
     * Submits [lines] as one order; only on success is [onOrderCommitted]
     * run (which clears the bag). Errors surface in [orderState].
     */
    fun submitOrder(
        form: CheckoutForm,
        lines: List<CartLine>,
        onOrderCommitted: (PlacedOrder) -> Unit,
    ) {
        if (orderState is OrderState.Submitting) return
        orderState = OrderState.Submitting
        scope.launch {
            try {
                val order = repository.createOrder(form, lines)
                onOrderCommitted(order)
                orderState = OrderState.Success(order)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                orderState = OrderState.Error(error.readableMessage())
            }
        }
    }
}
