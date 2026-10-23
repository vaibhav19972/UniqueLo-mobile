package com.uniquelo.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uniquelo.app.data.ThemePersistence
import com.uniquelo.app.model.ThemeMode

/**
 * App-scoped appearance preference. Read synchronously at construction (the
 * same accepted MVP trade-off as the catalogue cache, OA-4) and written
 * atomically on change. [ThemeMode.System] is the safe default when no
 * preference exists.
 */
class ThemeStore(
    private val persistence: ThemePersistence? = null,
) {
    var mode by mutableStateOf(persistence?.load() ?: ThemeMode.System)
        private set

    fun select(next: ThemeMode) {
        if (mode == next) return
        mode = next
        persistence?.save(next)
    }
}
