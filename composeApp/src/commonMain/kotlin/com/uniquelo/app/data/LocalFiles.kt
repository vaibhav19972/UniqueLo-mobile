package com.uniquelo.app.data

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Absolute path of the app's private, persistent directory (survives app and
 * device restarts), or null when one is not available — for example in unit
 * tests, where persistence is simply disabled.
 */
internal expect fun platformAppFilesDir(): String?

/** Returns the platform directory if it exists and can be used, else null. */
private fun appFilesPath(): Path? {
    val raw = platformAppFilesDir() ?: return null
    return runCatching {
        val path = raw.toPath()
        FileSystem.SYSTEM.createDirectories(path)
        path
    }.getOrNull()
}

/** Disk-backed catalogue cache when the platform allows it, else null. */
internal fun defaultSnapshotCache(): SnapshotCache? = runCatching {
    appFilesPath()?.let(::OkioSnapshotCache)
}.getOrNull()

/** Disk-backed bag journal when the platform allows it, else null. */
internal fun defaultBagPersistence(): BagPersistence? = runCatching {
    appFilesPath()?.let(::OkioBagPersistence)
}.getOrNull()

/** Disk-backed appearance preference when the platform allows it, else null. */
internal fun defaultThemePersistence(): ThemePersistence? = runCatching {
    appFilesPath()?.let(::OkioThemePersistence)
}.getOrNull()

/** Disk-backed wishlist journal when the platform allows it, else null. */
internal fun defaultWishlistPersistence(): WishlistPersistence? = runCatching {
    appFilesPath()?.let(::OkioWishlistPersistence)
}.getOrNull()

/** Disk-backed order history journal when the platform allows it, else null. */
internal fun defaultOrdersPersistence(): OrdersPersistence? = runCatching {
    appFilesPath()?.let(::OkioOrdersPersistence)
}.getOrNull()
