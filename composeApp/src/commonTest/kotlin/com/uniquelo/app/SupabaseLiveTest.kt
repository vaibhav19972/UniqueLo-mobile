package com.uniquelo.app

import com.uniquelo.app.data.SupabaseRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live integration check against the real project. Ignored by default so unit
 * tests stay offline; remove @Ignore (or run from the IDE) when verifying the
 * Supabase connection.
 */
class SupabaseLiveTest {
    @Ignore
    @Test
    fun catalogueDecodesFromLiveProject() = runBlocking {
        val snapshot = SupabaseRepository().loadCatalog()

        assertTrue(snapshot.products.size >= 4)
        assertEquals(3, snapshot.universes.size)
        assertTrue(snapshot.products.all { it.variants.isNotEmpty() })
        assertTrue(snapshot.products.any { it.primaryImageUrl != null })
        assertTrue(snapshot.products.any { it.embroidery != null })
        assertTrue(snapshot.products.any { it.reviews.isNotEmpty() })
        println(snapshot.products.joinToString("\n") { p ->
            "${p.slug}: ${p.variants.size} variants, ${p.remoteImages.size} images, " +
                "₹${p.minPriceCents}, ${p.universeName}, ${p.reviews.size} reviews"
        })
    }
}
