package com.uniquelo.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Caches the loaded [CatalogSnapshot] on disk so a cold start can paint the
 * catalogue instantly and keep working while offline, before revalidating
 * against the network (stale-while-revalidate).
 */
interface SnapshotCache {
    /** Last saved snapshot, or null. Cheap blocking read. */
    fun load(): CatalogSnapshot?

    fun save(snapshot: CatalogSnapshot)

    fun clear()
}

@Serializable
internal data class CacheEnvelope(
    val version: Int = CACHE_VERSION,
    val snapshot: CatalogSnapshot,
)

internal const val CACHE_VERSION = 1

class OkioSnapshotCache(
    directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : SnapshotCache {

    private val file = directory / "catalog-cache.json"
    private val temp = directory / "catalog-cache.json.tmp"

    init {
        fileSystem.createDirectories(directory)
    }

    override fun load(): CatalogSnapshot? = runCatching {
        if (!fileSystem.exists(file)) return null
        fileSystem.source(file).buffer().use { source ->
            val envelope = SupabaseJson.decodeFromString<CacheEnvelope>(source.readUtf8())
            envelope.takeIf { it.version == CACHE_VERSION }?.snapshot
        }
    }.getOrNull()

    override fun save(snapshot: CatalogSnapshot) {
        runCatching {
            // Write then rename: a crash mid-write leaves the previous cache
            // intact rather than a half-written JSON document.
            fileSystem.write(temp) {
                writeUtf8(SupabaseJson.encodeToString(CacheEnvelope(snapshot = snapshot)))
            }
            fileSystem.atomicMove(temp, file)
        }
    }

    override fun clear() {
        runCatching {
            if (fileSystem.exists(file)) fileSystem.delete(file)
            if (fileSystem.exists(temp)) fileSystem.delete(temp)
        }
    }
}
