package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.UniqueLoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface SubscribeState {
    data object Idle : SubscribeState
    data object Submitting : SubscribeState
    data object Success : SubscribeState
    data class Error(val message: String) : SubscribeState
}

/** Newsletter signup state; small enough to stay a first-class store so the
 * Discover screen receives a narrow slice instead of all of RootStore. */
class NewsletterStore(
    private val repository: UniqueLoRepository,
    private val scope: CoroutineScope,
) {
    var subscribeEmail by mutableStateOf("")
    var subscribeState by mutableStateOf<SubscribeState>(SubscribeState.Idle)
        private set

    fun clearSubscribeError() {
        if (subscribeState is SubscribeState.Error) subscribeState = SubscribeState.Idle
    }

    fun subscribe(email: String = subscribeEmail) {
        if (subscribeState is SubscribeState.Submitting) return
        val clean = email.trim()
        if (!EMAIL.matches(clean)) {
            subscribeState = SubscribeState.Error("Enter a valid email address.")
            return
        }
        subscribeState = SubscribeState.Submitting
        scope.launch {
            try {
                repository.subscribe(clean)
                subscribeEmail = ""
                subscribeState = SubscribeState.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                val message = error.message.orEmpty()
                subscribeState = SubscribeState.Error(
                    if (message.contains("duplicate", ignoreCase = true) ||
                        message.contains("unique", ignoreCase = true)
                    ) {
                        "You are already on the list."
                    } else {
                        "Couldn't subscribe right now. Please try again."
                    },
                )
            }
        }
    }

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
