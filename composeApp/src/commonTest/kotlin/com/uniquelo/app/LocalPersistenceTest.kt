package com.uniquelo.app

import com.uniquelo.app.data.CatalogSnapshot
import com.uniquelo.app.data.OkioBagPersistence
import com.uniquelo.app.data.OkioSnapshotCache
import com.uniquelo.app.data.OkioThemePersistence
import com.uniquelo.app.data.SavedLine
import com.uniquelo.app.model.Category
import com.uniquelo.app.model.Collection
import com.uniquelo.app.model.Motif
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Review
import com.uniquelo.app.model.ThemeMode
import com.uniquelo.app.model.ThemeTokens
import com.uniquelo.app.model.Universe
import com.uniquelo.app.model.Variant
import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Round-trip behaviour of the disk-backed catalogue cache and bag journal. */
class LocalPersistenceTest {

    private val fileSystem = FakeFileSystem()

    @Test
    fun snapshotRoundTripsThroughDisk() {
        val dir = "/app/private".toPath()
        val cache = OkioSnapshotCache(dir, fileSystem)
        val snapshot = fixtureCatalog()

        assertNull(cache.load())
        cache.save(snapshot)

        val loaded = cache.load()
        assertEquals(snapshot, loaded)
    }

    @Test
    fun corruptSnapshotFileLoadsAsNull() {
        val dir = "/app/private".toPath()
        val cache = OkioSnapshotCache(dir, fileSystem)
        cache.save(fixtureCatalog())
        val file = "/app/private/catalog-cache.json".toPath()
        fileSystem.sink(file).buffer().use { it.writeUtf8("{not json") }

        assertNull(cache.load())
    }

    @Test
    fun bagJournalRoundTripsThroughDisk() {
        val dir = "/app/private".toPath()
        val bag = OkioBagPersistence(dir, fileSystem)
        val lines = listOf(
            SavedLine("kurta", "kurta-m", quantity = 4),
            SavedLine("hoodie", "hoodie-s", quantity = 1),
        )

        assertTrue(bag.load().isEmpty())
        bag.save(lines)
        assertEquals(lines, bag.load())

        bag.clear()
        assertTrue(bag.load().isEmpty())
    }

    /** An all-empty write must not leave a stale file claiming lines. */
    @Test
    fun savingEmptyBagClearsTheJournal() {
        val dir = "/app/private".toPath()
        val bag = OkioBagPersistence(dir, fileSystem)

        bag.save(listOf(SavedLine("kurta", "kurta-s", 2)))
        bag.save(emptyList())

        assertTrue(bag.load().isEmpty())
    }

    /** A missing parent directory must be created quietly by the cache init. */
    @Test
    fun cacheCreatesItsOwnDirectory() {
        val dir = "/never/seen".toPath()
        val cache = OkioSnapshotCache(dir, fileSystem)

        assertTrue(fileSystem.exists(dir))
        assertNull(cache.load())
    }

    @Test
    fun themePreferenceRoundTripsAndOverwritesAtomically() {
        val dir = "/app/private".toPath()
        val theme = OkioThemePersistence(dir, fileSystem)

        assertNull(theme.load())
        theme.save(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, theme.load())

        theme.save(ThemeMode.Light)
        assertEquals(ThemeMode.Light, theme.load())

        // A fresh instance (next launch) reads the same file.
        assertEquals(ThemeMode.Light, OkioThemePersistence(dir, fileSystem).load())
        assertTrue(!fileSystem.exists("/app/private/theme.txt.tmp".toPath()))
    }

    @Test
    fun corruptThemePreferenceFallsBackToSystem() {
        val dir = "/app/private".toPath()
        val theme = OkioThemePersistence(dir, fileSystem)
        val file = "/app/private/theme.txt".toPath()
        fileSystem.sink(file).buffer().use { it.writeUtf8("sepia") }

        assertNull(theme.load())
    }

    @Test
    fun versionMismatchIsTreatedAsMissingCache() {
        val dir = "/app/private".toPath()
        val cache = OkioSnapshotCache(dir, fileSystem)
        // Write through the disk, then tamper with the version field.
        cache.save(fixtureCatalog())
        val file = "/app/private/catalog-cache.json".toPath()
        fileSystem.sink(file).buffer().use {
            it.writeUtf8("""{"version":999,"snapshot":{"products":[],"universes":[],"categories":[],"collections":[],"motifs":[],"reviews":[]}}""")
        }

        assertNull(cache.load())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fixture
    // ─────────────────────────────────────────────────────────────────────────

    private fun fixtureCatalog(): CatalogSnapshot {
        val universe = Universe(
            slug = "itihaas",
            name = "Itihaas",
            tagline = "Threads of a brighter Bharat.",
            lore = "Lore",
            dropCadence = null,
            theme = ThemeTokens(bg = "#E7E0CF", ink = "#15150F", accent = "#DDFF57"),
            orderNum = 1,
        )
        val product = Product(
            slug = "kurta",
            name = "Embroidered Charcoal Kurta",
            subtitle = "Test piece",
            foreignTagline = null,
            description = "Hand embroidered.",
            categorySlug = "knitwear",
            universeSlug = "itihaas",
            collectionSlug = null,
            garmentType = "kurta",
            gender = "unisex",
            motifId = null,
            handcrafted = true,
            craftTechniques = listOf("chikankari"),
            edits = listOf("everyday"),
            expressionScore = 4,
            rating = 4.8,
            reviewCount = 7,
            soldCount = "50+",
            tags = listOf("embroidery"),
            featured = true,
            customizable = false,
            material = listOf("Organic cotton"),
            care = listOf("Cold wash"),
            fit = "Boxy",
            batchNumber = 1,
            batchTotal = 50,
            craftRegion = "Lucknow",
            craftCluster = "Chikankari Guild",
            fabricGsm = "280 GSM",
            variants = listOf(
                Variant(
                    sku = "kurta-s",
                    productSlug = "kurta",
                    color = "Obsidian",
                    colorHex = "#121214",
                    size = "S",
                    priceCents = 149900,
                    compareAtPriceCents = null,
                    status = "in-stock",
                    quantity = 10,
                ),
            ),
            images = emptyList(),
            embroidery = null,
            reviews = listOf(
                Review(id = "r1", productSlug = "kurta", author = "QA", rating = 5, title = "Lovely", comment = "Fine.", verified = true),
            ),
            universe = universe,
            category = Category("knitwear", "Knitwear", "Knitwear", ""),
            collection = Collection("edits-01", "First Edits", null, null, false),
            motif = Motif(id = "m1", universeSlug = "itihaas", name = "Paisley", meaning = "Fertility", symbolNotes = null),
        )
        return CatalogSnapshot(
            products = listOf(product),
            collections = emptyList(),
            categories = emptyList(),
            motifs = emptyList(),
            reviews = emptyList(),
            universes = listOf(universe),
        )
    }
}
