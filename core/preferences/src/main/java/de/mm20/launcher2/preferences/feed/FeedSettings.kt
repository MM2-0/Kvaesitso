package de.mm20.launcher2.preferences.feed

import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class FeedSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {

    val enabled
        get() = launcherDataStore.data.map { it.gesturesSwipeRight is GestureAction.Feed }

    fun setEnabled(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(gesturesSwipeRight = if (enabled) GestureAction.Feed else GestureAction.NoAction)
        }
    }

    val providerPackage
        get() = launcherDataStore.data.map { it.feedProviderPackage }

    fun setProviderPackage(providerPackage: String?) {
        launcherDataStore.update {
            it.copy(feedProviderPackage = providerPackage)
        }
    }

}