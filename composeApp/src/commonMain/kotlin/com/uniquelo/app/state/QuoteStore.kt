package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.QuoteResponse
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.data.readableMessage
import com.uniquelo.app.model.CartLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface QuoteState {
    data object Idle : QuoteState
    data object Loading : QuoteState
    data class Ready(val quote: QuoteResponse) : QuoteState
    data class Failed(val reason: String) : QuoteState
}

/**
 * Manages server quote orchestration for the customer bag and checkout.
 * Re-quotes whenever bag lines, coupon code, or payment method change,
 * cancelling previous jobs and dropping stale responses.
 */
class QuoteStore(
    private val repository: UniqueLoRepository,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
) {
    var state by mutableStateOf<QuoteState>(QuoteState.Idle)
        private set

    var couponCode by mutableStateOf<String?>(null)
        private set

    var paymentMethod by mutableStateOf("cod")
        private set

    private var currentLines: List<CartLine> = emptyList()
    private var quoteJob: Job? = null
    private var activeRequestId = 0L

    val isReady: Boolean get() = state is QuoteState.Ready
    val currentQuote: QuoteResponse? get() = (state as? QuoteState.Ready)?.quote

    fun onBagChanged(lines: List<CartLine>) {
        currentLines = lines.toList()
        if (currentLines.isEmpty()) {
            quoteJob?.cancel()
            state = QuoteState.Idle
            return
        }
        scheduleQuote()
    }

    fun applyCoupon(code: String?) {
        val clean = code?.trim()?.ifBlank { null }
        if (couponCode == clean) return
        couponCode = clean
        scheduleQuote()
    }

    fun selectPaymentMethod(method: String) {
        val normalized = if (method.equals("cod", ignoreCase = true)) "cod" else "prepaid"
        if (paymentMethod == normalized) return
        paymentMethod = normalized
        scheduleQuote()
    }

    fun retry() {
        scheduleQuote(immediate = true)
    }

    private fun scheduleQuote(immediate: Boolean = false) {
        if (currentLines.isEmpty()) {
            quoteJob?.cancel()
            state = QuoteState.Idle
            return
        }

        val linesToQuote = currentLines
        val couponToQuote = couponCode
        val paymentToQuote = paymentMethod
        val requestId = ++activeRequestId

        quoteJob?.cancel()
        state = QuoteState.Loading

        quoteJob = scope.launch {
            try {
                if (!immediate && debounceMs > 0) {
                    delay(debounceMs)
                }
                val response = repository.quoteOrder(
                    lines = linesToQuote,
                    couponCode = couponToQuote,
                    paymentMethod = paymentToQuote,
                )
                if (requestId == activeRequestId) {
                    state = QuoteState.Ready(response)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (requestId == activeRequestId) {
                    state = QuoteState.Failed(error.readableMessage())
                }
            }
        }
    }
}
