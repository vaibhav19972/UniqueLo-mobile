package com.uniquelo.app

import com.uniquelo.app.model.ThemeMode
import com.uniquelo.app.state.ThemeStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Persisted 3-state appearance preference (D-16). */
class ThemeStoreTest {

    @Test
    fun defaultsToSystemWithoutPersistence() {
        assertEquals(ThemeMode.System, ThemeStore(null).mode)
    }

    @Test
    fun restoresStoredPreference() {
        val persistence = FakeThemePersistence(stored = ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, ThemeStore(persistence).mode)
    }

    @Test
    fun selectingWritesThroughAndSurvivesRecreation() {
        val persistence = FakeThemePersistence()

        val store = ThemeStore(persistence)
        store.select(ThemeMode.Light)

        assertEquals(ThemeMode.Light, store.mode)
        assertEquals(ThemeMode.Light, persistence.stored)
        assertEquals(1, persistence.saves)

        // A new store (next cold launch) reads the same preference.
        assertEquals(ThemeMode.Light, ThemeStore(persistence).mode)
    }

    @Test
    fun reselectingTheCurrentModeDoesNotWrite() {
        val persistence = FakeThemePersistence(stored = ThemeMode.Dark)
        val store = ThemeStore(persistence)

        store.select(ThemeMode.Dark)

        assertEquals(0, persistence.saves)
    }
}
