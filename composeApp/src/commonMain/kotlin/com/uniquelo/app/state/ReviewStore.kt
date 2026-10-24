package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.ReviewDraft
import com.uniquelo.app.data.UniqueLoRepository
import com.uniquelo.app.data.readableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface ReviewState {
    data object Idle : ReviewState
    data object Submitting : ReviewState
    data object Success : ReviewState
    data class Error(val message: String) : ReviewState
}

class ReviewStore(
    private val repository: UniqueLoRepository,
    private val scope: CoroutineScope,
    private val onReviewSubmitted: (productSlug: String) -> Unit = {},
) {
    var rating by mutableStateOf(0)
        private set
    var author by mutableStateOf("")
    var title by mutableStateOf("")
    var comment by mutableStateOf("")
    var state by mutableStateOf<ReviewState>(ReviewState.Idle)
        private set

    fun selectRating(value: Int) {
        if (value in 1..5) {
            rating = value
        }
    }

    fun reset() {
        rating = 0
        title = ""
        comment = ""
        state = ReviewState.Idle
    }

    fun submit(productSlug: String) {
        if (state is ReviewState.Submitting) return
        if (rating !in 1..5) {
            state = ReviewState.Error("Pick a star rating.")
            return
        }
        if (author.trim().isBlank()) {
            state = ReviewState.Error("Add your name.")
            return
        }
        if (title.trim().isBlank()) {
            state = ReviewState.Error("Add a headline.")
            return
        }
        if (comment.trim().isBlank()) {
            state = ReviewState.Error("Tell us what you think.")
            return
        }

        state = ReviewState.Submitting
        scope.launch {
            try {
                repository.submitReview(
                    ReviewDraft(
                        productSlug = productSlug.trim(),
                        author = author.trim(),
                        rating = rating,
                        title = title.trim(),
                        comment = comment.trim(),
                    ),
                )
                rating = 0
                title = ""
                comment = ""
                state = ReviewState.Success
                onReviewSubmitted(productSlug)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                val raw = error.message.orEmpty()
                state = if (raw.contains("duplicate", ignoreCase = true) ||
                    raw.contains("unique", ignoreCase = true)
                ) {
                    ReviewState.Error("You have already reviewed this piece.")
                } else {
                    ReviewState.Error(readableMessage(raw))
                }
            }
        }
    }
}
