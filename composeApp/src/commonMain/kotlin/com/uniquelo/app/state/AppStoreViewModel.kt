package com.uniquelo.app.state

import androidx.lifecycle.ViewModel

/**
 * Platform bridge that registers the app [Store] with the ViewModelStoreOwner
 * (the activity on Android, the view controller on iOS) so it survives
 * configuration changes and window recreation. The store itself stays a plain
 * framework-agnostic state holder; this class is the only ViewModel in the app.
 */
class AppStoreViewModel : ViewModel() {
    val store: Store = Store()

    override fun onCleared() {
        store.close()
    }
}
