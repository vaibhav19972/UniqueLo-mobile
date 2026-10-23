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
            fileSystem.write(file, mustCreate = false) {
                if (lines.isEmpty()) {
                    // An empty bag clears the journal.
                    writeUtf8("")
                } else {
                    writeUtf8(SupabaseJson.encodeToString(lines))
                }
            }
            if (lines.isEmpty()) runCatching { fileSystem.delete(file) }
        }
    }

    override fun clear() {
        runCatching {
            fileSystem.delete(file)
        }
    }
}
