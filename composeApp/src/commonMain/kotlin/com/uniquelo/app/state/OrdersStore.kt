package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.OrdersPersistence
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.model.LocalOrderSummary
import com.uniquelo.app.model.OrderDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface OrderLookupState {
    data object Idle : OrderLookupState
    data object Loading : OrderLookupState
    data class Success(val details: OrderDetails) : OrderLookupState
    data class Error(val message: String) : OrderLookupState
}

class OrdersStore(
    private val repository: UniqueLoRepository,
    private val scope: CoroutineScope,
    private val ordersStorage: OrdersPersistence? = null,
) {
    var orders by mutableStateOf<List<LocalOrderSummary>>(emptyList())
        private set

    var lookupState by mutableStateOf<OrderLookupState>(OrderLookupState.Idle)
        private set

    init {
        orders = ordersStorage?.load().orEmpty()
    }

    val count: Int get() = orders.size

    fun recordOrder(placed: PlacedOrder, totalCents: Int, itemCount: Int) {
        val summary = LocalOrderSummary(
            id = placed.id,
            code = placed.code,
            email = placed.email,
            totalCents = totalCents,
            itemCount = itemCount,
        )
        orders = listOf(summary) + orders.filterNot { it.id == placed.id }
        ordersStorage?.save(orders)
    }

    fun lookupOrder(orderId: String, email: String) {
        val cleanId = orderId.trim()
        val cleanEmail = email.trim().lowercase()

        if (cleanId.isEmpty() || cleanEmail.isEmpty()) {
            lookupState = OrderLookupState.Error("Please enter both Order ID and Email.")
            return
        }

        lookupState = OrderLookupState.Loading
        scope.launch {
            try {
                val details = repository.getOrderDetails(cleanId, cleanEmail)
                if (details != null) {
                    lookupState = OrderLookupState.Success(details)
                } else {
                    lookupState = OrderLookupState.Error(
                        "Order not found or the email provided does not match this commission record."
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                lookupState = OrderLookupState.Error(
                    error.message ?: "Could not retrieve order details. Please check your connection."
                )
            }
        }
    }

    fun clearLookup() {
        lookupState = OrderLookupState.Idle
    }
}

