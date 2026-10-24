package com.uniquelo.app.data

import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** Journal of saved product slugs; survives process death and offline starts. */
interface WishlistPersistence {
    fun load(): Set<String>
    fun save(slugs: Set<String>)
}

class OkioWishlistPersistence(
    directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : WishlistPersistence {

    private val file = directory / "wishlist.json"
    private val temp = directory / "wishlist.json.tmp"

    init {
        fileSystem.createDirectories(directory)
    }

    override fun load(): Set<String> = runCatching {
        if (!fileSystem.exists(file)) return emptySet()
        fileSystem.source(file).buffer().use { source ->
            SupabaseJson.decodeFromString<Set<String>>(source.readUtf8())
        }
    }.getOrNull().orEmpty()

    override fun save(slugs: Set<String>) {
        runCatching {
            if (slugs.isEmpty()) {
                if (fileSystem.exists(file)) fileSystem.delete(file)
                if (fileSystem.exists(temp)) fileSystem.delete(temp)
                return
            }
            fileSystem.write(temp) {
                writeUtf8(SupabaseJson.encodeToString(slugs))
            }
            fileSystem.atomicMove(temp, file)
        }
    }
}
