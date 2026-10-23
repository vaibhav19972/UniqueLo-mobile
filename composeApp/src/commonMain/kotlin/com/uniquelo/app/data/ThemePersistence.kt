package com.uniquelo.app.data

import com.uniquelo.app.model.ThemeMode
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Persists the appearance preference next to the catalogue cache and bag
 * journal. A tiny text file is deliberately used instead of JSON: the value is
 * one enum name and a corrupt file must degrade to [ThemeMode.System], never
 * fail app startup.
 */
interface ThemePersistence {
    fun load(): ThemeMode?
    fun save(mode: ThemeMode)
}

class OkioThemePersistence(
    directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : ThemePersistence {

    private val file = directory / "theme.txt"
    private val temp = directory / "theme.txt.tmp"

    init {
        fileSystem.createDirectories(directory)
    }

    override fun load(): ThemeMode? = runCatching {
        if (!fileSystem.exists(file)) return null
        fileSystem.source(file).buffer().use { source ->
            ThemeMode.fromStorage(source.readUtf8())
        }
    }.getOrNull()

    override fun save(mode: ThemeMode) {
        runCatching {
            // Write then rename so a crash mid-write cannot leave a partial
            // preference that changes the app's appearance on next launch.
            fileSystem.write(temp) {
                writeUtf8(mode.name)
            }
            fileSystem.atomicMove(temp, file)
        }
    }
}
