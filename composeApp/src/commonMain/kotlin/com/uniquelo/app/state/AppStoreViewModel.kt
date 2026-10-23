package com.uniquelo.app.state

import androidx.lifecycle.ViewModel

/**
 * ViewModel-owned store facade (survives configurations change, process death
 * is handled by the stores' own persistence). `onCleared` cancels the shared
 * scope so in-flight refreshes cannot outlive the screen (audit A6/A7).
 */
class AppStoreViewModel : ViewModel() {
    val store: RootStore = AppGraph.createStore()

    override fun onCleared() {
        store.close()
    }
}
