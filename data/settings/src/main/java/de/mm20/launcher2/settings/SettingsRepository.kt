package de.mm20.launcher2.settings

import android.content.Context
import android.provider.Settings
import de.mm20.launcher2.preferences.search.SettingsSearchSettings
import de.mm20.launcher2.search.data.PojoSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

interface SettingsRepository {
    fun search(query: String) : Flow<ImmutableList<PojoSettings>>
}

class SettingsRepositoryImpl(
    private val context: Context,
    private val settings: SettingsSearchSettings
) : SettingsRepository, KoinComponent {

    override fun search(query: String): Flow<ImmutableList<PojoSettings>> {
        return settings.enabled.map {
            if (it && query.isNotBlank()) {
                filteredSettingsList(query)
            } else {
                persistentListOf()
            }
        }
    }

    private suspend fun filteredSettingsList(query: String): ImmutableList<PojoSettings> {
        return withContext(Dispatchers.IO) {
            getKnownSettingsList().filter {
                it.label.contains(query, ignoreCase = true)
            }.toImmutableList()
        }
    }
    private fun getKnownSettingsList(): List<PojoSettings> =
        listOf(
            PojoSettings(
                titleForPage = R.string.settings_airplane,
                icon = 0,

                actionId = Settings.ACTION_AIRPLANE_MODE_SETTINGS,

                key = "settings-airplane",
                label = context.getString(R.string.settings_airplane)
            ),
            PojoSettings(
                titleForPage = R.string.settings_deviceinfo,
                icon = 1,

                actionId = Settings.ACTION_DEVICE_INFO_SETTINGS,

                key = "settings-device",
                label = context.getString(R.string.settings_deviceinfo)
            ),
            PojoSettings(
                titleForPage = R.string.settings_manage_applications,
                icon = 2,

                actionId = Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,

                key = "settings-apps",
                label = context.getString(R.string.settings_manage_applications)
            ),
            PojoSettings(
                titleForPage = R.string.app_name,
                icon = 3,

                specialId = PojoSettings.specialIdLauncher,

                key = "settings-launcher",
                label = context.getString(R.string.app_name)
            )
        )
}