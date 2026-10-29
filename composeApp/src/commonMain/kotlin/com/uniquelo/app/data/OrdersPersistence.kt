package com.uniquelo.app.data

import com.uniquelo.app.model.LocalOrderSummary
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** Journal of placed orders; survives process death and device restarts. */
interface OrdersPersistence {
    fun load(): List<LocalOrderSummary>
    fun save(orders: List<LocalOrderSummary>)
}

class OkioOrdersPersistence(
    directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : OrdersPersistence {

    private val file = directory / "orders.json"
    private val temp = directory / "orders.json.tmp"

    init {
        fileSystem.createDirectories(directory)
    }

    override fun load(): List<LocalOrderSummary> = runCatching {
        if (!fileSystem.exists(file)) return emptyList()
        fileSystem.source(file).buffer().use { source ->
            SupabaseJson.decodeFromString<List<LocalOrderSummary>>(source.readUtf8())
        }
    }.getOrNull().orEmpty()

    override fun save(orders: List<LocalOrderSummary>) {
        runCatching {
            if (orders.isEmpty()) {
                if (fileSystem.exists(file)) fileSystem.delete(file)
                if (fileSystem.exists(temp)) fileSystem.delete(temp)
                return
            }
            fileSystem.write(temp) {
                writeUtf8(SupabaseJson.encodeToString(orders))
            }
            fileSystem.atomicMove(temp, file)
        }
    }
}

