package de.mm20.launcher2.preferences.media

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MediaSettingsData(
    val allowList: Set<String>,
    val denyList: Set<String>,
)

class MediaSettings internal constructor(
    private val launcherDataStore: LauncherDataStore
) : Flow<MediaSettingsData> by (launcherDataStore.data.map {
    MediaSettingsData(
        it.mediaAllowList,
        it.mediaDenyList
    )
}) {
    val allowList
        get() = launcherDataStore.data.map { it.mediaAllowList }

    fun setLists(allowList: Set<String>, denyList: Set<String>) {
        launcherDataStore.update {
            it.copy(mediaAllowList = allowList, mediaDenyList = denyList)
        }
    }

    val denyList
        get() = launcherDataStore.data.map { it.mediaDenyList }
}