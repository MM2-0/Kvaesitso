package de.mm20.launcher2.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
            getKnownSettingsList(context).filter {
                context.getString(R.string.preference_search_settings).contains(query, ignoreCase = true) ||
                it.label.contains(query, ignoreCase = true)
            }.toImmutableList()
        }
    }
    private fun getKnownSettingsList(context: Context): List<PojoSettings> {
        val pm = context.packageManager
        val list = mutableListOf<PojoSettings>()

        list.addAll(
            listOf(
                PojoSettings(
                    icon = R.drawable.ic_settings_airplane,
                    actionId = Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                    key = "settings-airplane",
                    label = context.getString(R.string.settings_airplane)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_deviceinfo,
                    actionId = Settings.ACTION_DEVICE_INFO_SETTINGS,
                    key = "settings-device",
                    label = context.getString(R.string.settings_deviceinfo)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_apps,
                    actionId = Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                    key = "settings-apps",
                    label = context.getString(R.string.settings_manage_applications)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_storage,
                    actionId = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                    key = "settings-storage",
                    label = context.getString(R.string.settings_storage)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_wireless,
                    actionId = Settings.ACTION_WIRELESS_SETTINGS,
                    key = "settings-wireless",
                    label = context.getString(R.string.settings_wireless)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_tethering,
                    actionId = "com.android.settings.TetherSettings",
                    packageName = "com.android.settings",
                    key = "settings-tethering",
                    label = context.getString(R.string.settings_tethering)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_accessibility,
                    actionId = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                    key = "settings-accessibility",
                    label = context.getString(R.string.settings_accessibility)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_battery,
                    actionId = Intent.ACTION_POWER_USAGE_SUMMARY,
                    key = "settings-battery",
                    label = context.getString(R.string.settings_battery)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_sound,
                    actionId = Settings.ACTION_SOUND_SETTINGS,
                    key = "settings-sound",
                    label = context.getString(R.string.settings_sound)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_display,
                    actionId = Settings.ACTION_DISPLAY_SETTINGS,
                    key = "settings-display",
                    label = context.getString(R.string.settings_display)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_developer,
                    actionId = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                    key = "settings-developer",
                    label = context.getString(R.string.settings_developer)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_privacy,
                    actionId = Settings.ACTION_SECURITY_SETTINGS,
                    key = "settings-security",
                    label = context.getString(R.string.settings_security)
                ),
                PojoSettings(
                    icon = R.drawable.ic_settings_updates,
                    actionId = "android.settings.SYSTEM_UPDATE_SETTINGS",
                    key = "settings-updates",
                    label = context.getString(R.string.settings_updates)
                ),


                PojoSettings(
                    icon = R.drawable.ic_settings_launcher,
                    specialId = PojoSettings.specialIdLauncher,
                    key = "settings-launcher",
                    label = context.getString(R.string.app_name)
                ),
            )
        )

        if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            list.add(PojoSettings(
                icon = R.drawable.ic_settings_wifi,
                actionId = Settings.ACTION_WIFI_SETTINGS,
                key = "settings-wifi",
                label = context.getString(R.string.settings_wifi)
            ))
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            list.add(PojoSettings(
                icon = R.drawable.ic_settings_bluetooth,
                actionId = Settings.ACTION_BLUETOOTH_SETTINGS,
                key = "settings-bluetooth",
                label = context.getString(R.string.settings_bluetooth)
            ))
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            list.add(PojoSettings(
                icon = R.drawable.ic_settings_nfc,
                actionId = Settings.ACTION_NFC_SETTINGS,
                key = "settings-nfc",
                label = context.getString(R.string.settings_nfc)
            ))
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
            list.add(PojoSettings(
                icon = R.drawable.ic_settings_location,
                actionId = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                key = "settings-location",
                label = context.getString(R.string.settings_location)
            ))
        }

        return list
    }

}