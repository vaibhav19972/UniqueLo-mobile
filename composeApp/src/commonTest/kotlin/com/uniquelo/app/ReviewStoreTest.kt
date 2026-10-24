package com.uniquelo.app

import com.uniquelo.app.data.SupabaseException
import com.uniquelo.app.data.readableMessage
import com.uniquelo.app.state.ReviewState
import com.uniquelo.app.state.ReviewStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewStoreTest {

    private fun testSnapshot() = catalogFixture(listOf(kurta))

    @Test
    fun submitRejectsMissingRating() {
        val fake = FakeRepository(testSnapshot())
        val store = ReviewStore(fake, unconfinedScope)
        store.author = "Asha"
        store.title = "Beautiful finish"
        store.comment = "The embroidery is delicate and very well done."

        store.submit("kurta")

        assertEquals(ReviewState.Error("Pick a star rating."), store.state)
        assertTrue(fake.submittedReviews.isEmpty())
    }

    @Test
    fun submitRejectsBlankAuthor() {
        val fake = FakeRepository(testSnapshot())
        val store = ReviewStore(fake, unconfinedScope)
        store.selectRating(5)
        store.author = "   "
        store.title = "Beautiful finish"
        store.comment = "The embroidery is delicate and very well done."

        store.submit("kurta")

        assertEquals(ReviewState.Error("Add your name."), store.state)
        assertTrue(fake.submittedReviews.isEmpty())
    }

    @Test
    fun submitRejectsBlankTitle() {
        val fake = FakeRepository(testSnapshot())
        val store = ReviewStore(fake, unconfinedScope)
        store.selectRating(5)
        store.author = "Asha"
        store.title = "   "
        store.comment = "The embroidery is delicate and very well done."

        store.submit("kurta")

        assertEquals(ReviewState.Error("Add a headline."), store.state)
        assertTrue(fake.submittedReviews.isEmpty())
    }

    @Test
    fun submitRejectsBlankComment() {
        val fake = FakeRepository(testSnapshot())
        val store = ReviewStore(fake, unconfinedScope)
        store.selectRating(5)
        store.author = "Asha"
        store.title = "Beautiful finish"
        store.comment = "   "

        store.submit("kurta")

        assertEquals(ReviewState.Error("Tell us what you think."), store.state)
        assertTrue(fake.submittedReviews.isEmpty())
    }

    @Test
    fun successfulSubmitClearsDraftButKeepsAuthor() {
        val fake = FakeRepository(testSnapshot())
        val store = ReviewStore(fake, unconfinedScope)
        store.selectRating(5)
        store.author = "Asha"
        store.title = "Beautiful finish"
        store.comment = "The embroidery is delicate and very well done."

        store.submit("kurta")

        assertEquals(1, fake.submittedReviews.size)
        val submitted = fake.submittedReviews.single()
        assertEquals("kurta", submitted.productSlug)
        assertEquals(5, submitted.rating)
        assertEquals("Asha", submitted.author)
        assertEquals("Beautiful finish", submitted.title)
        assertEquals("The embroidery is delicate and very well done.", submitted.comment)

        assertEquals(0, store.rating)
        assertEquals("", store.title)
        assertEquals("", store.comment)
        assertEquals("Asha", store.author)
        assertEquals(ReviewState.Success, store.state)
    }

    @Test
    fun picksUpTheCallbackOnSuccess() {
        val fake = FakeRepository(testSnapshot())
        var receivedSlug: String? = null
        val store = ReviewStore(
            repository = fake,
            scope = unconfinedScope,
            onReviewSubmitted = { receivedSlug = it },
        )
        store.selectRating(4)
        store.author = "Kabir"
        store.title = "Comfortable fit"
        store.comment = "Great daily wear."

        store.submit("kurta")

        assertEquals("kurta", receivedSlug)
        assertEquals(ReviewState.Success, store.state)
    }

    @Test
    fun failureSurfacesReadableMessage() {
        val fake = FakeRepository(testSnapshot())
        fake.reviewFailures = 1
        val store = ReviewStore(fake, unconfinedScope)
        store.selectRating(5)
        store.author = "Asha"
        store.title = "Beautiful finish"
        store.comment = "The embroidery is delicate."

        store.submit("kurta")

        val expected = readableMessage("row-level security blocked review insert")
        assertEquals(ReviewState.Error(expected), store.state)
        assertTrue(fake.submittedReviews.isEmpty())
    }

    @Test
    fun duplicateIsReportedPolitely() {
        val fake = FakeRepository(testSnapshot())
        fake.reviewException = SupabaseException("duplicate key value violates unique constraint")
        val store = ReviewStore(fake, unconfinedScope)
        store.selectRating(5)
        store.author = "Asha"
        store.title = "Beautiful finish"
        store.comment = "The embroidery is delicate."

        store.submit("kurta")

        assertEquals(ReviewState.Error("You have already reviewed this piece."), store.state)
    }

    @Test
    fun resetReturnsToIdle() {
        val fake = FakeRepository(testSnapshot())
        val store = ReviewStore(fake, unconfinedScope)
        store.selectRating(5)
        store.author = "Asha"
        store.title = "Beautiful finish"
        store.comment = "The embroidery is delicate."

        store.submit("kurta")
        assertEquals(ReviewState.Success, store.state)

        store.reset()
        assertEquals(ReviewState.Idle, store.state)
        assertEquals(0, store.rating)
        assertEquals("", store.title)
        assertEquals("", store.comment)
        assertEquals("Asha", store.author)
    }
}
