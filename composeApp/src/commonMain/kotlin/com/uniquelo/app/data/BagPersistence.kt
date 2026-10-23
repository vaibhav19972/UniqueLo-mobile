package com.uniquelo.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * One bag line as persisted on disk: the minimum needed to rehydrate a
 * [com.uniquelo.app.model.CartLine] once a catalogue snapshot is available
 * (product slug + variant SKU + quantity). Product data itself is not
 * duplicated into the bag journal — the cached catalogue provides it.
 */
@Serializable
data class SavedLine(
    val productSlug: String,
    val sku: String,
    val quantity: Int,
)

/** Journal of the user's bag so it survives process death and offline starts. */
interface BagPersistence {
    fun load(): List<SavedLine>
    fun save(lines: List<SavedLine>)
    fun clear()
}

class OkioBagPersistence(
    directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : BagPersistence {

    private val file = directory / "bag.json"
    private val temp = directory / "bag.json.tmp"

    init {
        fileSystem.createDirectories(directory)
    }

    override fun load(): List<SavedLine> = runCatching {
        if (!fileSystem.exists(file)) return emptyList()
        fileSystem.source(file).buffer().use { source ->
            SupabaseJson.decodeFromString<List<SavedLine>>(source.readUtf8())
        }
    }.getOrNull().orEmpty()

    override fun save(lines: List<SavedLine>) {
        runCatching {
            if (lines.isEmpty()) {
                // An empty bag clears the journal.
                if (fileSystem.exists(file)) fileSystem.delete(file)
                if (fileSystem.exists(temp)) fileSystem.delete(temp)
                return
            }
            // Write then rename: a crash mid-write leaves the previous journal
            // intact rather than an empty/corrupt bag.
            fileSystem.write(temp) {
                writeUtf8(SupabaseJson.encodeToString(lines))
            }
            fileSystem.atomicMove(temp, file)
        }
    }

    override fun clear() {
        runCatching {
            if (fileSystem.exists(file)) fileSystem.delete(file)
        }
        runCatching {
            if (fileSystem.exists(temp)) fileSystem.delete(temp)
        }
    }
}
