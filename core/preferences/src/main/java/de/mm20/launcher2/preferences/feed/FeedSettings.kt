package de.mm20.launcher2.preferences.feed

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class FeedSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {

    val providerPackage
        get() = launcherDataStore.data.map { it.feedProviderPackage }

    fun setProviderPackage(providerPackage: String?) {
        launcherDataStore.update {
            it.copy(feedProviderPackage = providerPackage)
        }
    }
}