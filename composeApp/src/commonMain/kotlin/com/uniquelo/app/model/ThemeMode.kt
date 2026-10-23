package com.uniquelo.app.model

/**
 * User-selected appearance mode. [System] follows the platform setting; the
 * explicit modes let the user override it and are persisted across launches.
 */
enum class ThemeMode {
    System,
    Light,
    Dark;

    companion object {
        /** Storage value → enum, or null when the file is absent/corrupt. */
        fun fromStorage(raw: String?): ThemeMode? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
