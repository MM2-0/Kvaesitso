package de.mm20.launcher2.search

import android.content.Context
import android.provider.Settings
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class SettingsSearchRepository(private val context: Context) {

    fun search(
        query: String,
        android: Boolean,
        telos: Boolean,
        telosApps: Boolean,
    ): Flow<List<SettingsSearchItem>> = flow {
        if (query.isBlank() || (!android && !telos && !telosApps)) {
            emit(emptyList())
            return@flow
        }
        emit(
            withContext(Dispatchers.Default) {
                match(query.trim(), android, telos, telosApps)
            },
        )
    }

    private fun match(
        query: String,
        android: Boolean,
        telos: Boolean,
        telosApps: Boolean,
    ): List<SettingsSearchItem> {
        val catalog = catalog()
        return catalog.mapNotNull { item ->
            when (item.source) {
                SettingsSearchSource.Android -> if (!android) return@mapNotNull null
                SettingsSearchSource.Telos -> if (!telos) return@mapNotNull null
                SettingsSearchSource.TelosApp -> if (!telosApps) return@mapNotNull null
            }
            val score = ResultScore.from(
                query = query,
                primaryFields = listOf(item.label) + item.keywords,
                secondaryFields = listOf(item.subtitle),
            )
            if (score.score < ResultScore.MATCH_THRESHOLD) return@mapNotNull null
            item.copy(score = score)
        }.sortedByDescending { it.score.score }.take(24)
    }

    private fun catalog(): List<SettingsSearchItem> {
        val app = context.applicationContext
        val androidLabel = app.getString(I18nR.string.search_settings_source_android)
        val telosLabel = app.getString(I18nR.string.search_settings_source_telos)
        fun android(action: String, title: Int, icon: Int, vararg keys: String) = SettingsSearchItem(
            label = app.getString(title),
            subtitle = androidLabel,
            source = SettingsSearchSource.Android,
            androidAction = action,
            iconRes = icon,
            keywords = keys.toList(),
        )
        fun telos(route: String, title: Int, icon: Int, vararg keys: String) = SettingsSearchItem(
            label = app.getString(title),
            subtitle = telosLabel,
            source = SettingsSearchSource.Telos,
            telosRoute = route,
            iconRes = icon,
            keywords = keys.toList(),
        )
        fun appSetting(route: String, domain: String, extraKeys: List<String> = emptyList()): SettingsSearchItem {
            val name = TelosAppLabels.forDomain(app, domain)
            return SettingsSearchItem(
                label = name,
                subtitle = app.getString(I18nR.string.search_settings_source_telos_app, name),
                source = SettingsSearchSource.TelosApp,
                telosRoute = route,
                iconRes = TelosAppIcons.forDomain(domain) ?: BaseR.drawable.settings_24px,
                keywords = extraKeys,
            )
        }
        return listOf(
            android(Settings.ACTION_WIFI_SETTINGS, I18nR.string.search_android_wifi, BaseR.drawable.wifi_24px, "wifi", "wlan", "wi-fi"),
            android(Settings.ACTION_BLUETOOTH_SETTINGS, I18nR.string.search_android_bluetooth, BaseR.drawable.bluetooth_24px, "bluetooth", "bt"),
            android(Settings.ACTION_DISPLAY_SETTINGS, I18nR.string.search_android_display, BaseR.drawable.tune_24px, "display", "brightness", "screen"),
            android(Settings.ACTION_SOUND_SETTINGS, I18nR.string.search_android_sound, BaseR.drawable.volume_up_24px, "sound", "volume", "audio"),
            android(Settings.ACTION_DATE_SETTINGS, I18nR.string.search_android_date, BaseR.drawable.today_24px, "date", "time", "clock"),
            android(Settings.ACTION_LOCALE_SETTINGS, I18nR.string.search_android_language, BaseR.drawable.language_24px, "language", "locale"),
            android(Settings.ACTION_ACCESSIBILITY_SETTINGS, I18nR.string.search_android_accessibility, BaseR.drawable.settings_24px, "accessibility"),
            android(Settings.ACTION_SECURITY_SETTINGS, I18nR.string.search_android_security, BaseR.drawable.lock_24px, "security", "lock", "pin"),
            android(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, I18nR.string.search_android_storage, BaseR.drawable.hard_disk_24px, "storage", "memory"),
            android(Settings.ACTION_APPLICATION_SETTINGS, I18nR.string.search_android_apps, BaseR.drawable.apps_24px, "apps", "applications"),
            android(Settings.ACTION_LOCATION_SOURCE_SETTINGS, I18nR.string.search_android_location, BaseR.drawable.location_on_24px, "location", "gps"),
            android(Settings.ACTION_NFC_SETTINGS, I18nR.string.search_android_nfc, BaseR.drawable.bluetooth_24px, "nfc"),
            android(Settings.ACTION_AIRPLANE_MODE_SETTINGS, I18nR.string.search_android_airplane, BaseR.drawable.wifi_off_24px, "airplane", "flight"),
            android(Settings.ACTION_DATA_ROAMING_SETTINGS, I18nR.string.search_android_mobile_data, BaseR.drawable.signal_cellular_alt_24px, "data", "mobile", "cellular"),
            android(Settings.ACTION_VPN_SETTINGS, I18nR.string.search_android_vpn, BaseR.drawable.lock_24px, "vpn"),
            android(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, I18nR.string.search_android_notifications, BaseR.drawable.notifications_24px, "notifications"),
            android(Settings.ACTION_BATTERY_SAVER_SETTINGS, I18nR.string.search_android_battery, BaseR.drawable.battery_full_24px, "battery", "power"),
            android(Settings.ACTION_HOME_SETTINGS, I18nR.string.search_android_home, BaseR.drawable.home_24px, "home", "launcher", "default"),
            android(Settings.ACTION_SETTINGS, I18nR.string.search_android_all, BaseR.drawable.settings_24px, "settings", "system"),
            android(Settings.ACTION_DEVICE_INFO_SETTINGS, I18nR.string.search_android_about, BaseR.drawable.info_24px, "about", "device", "android"),
            android(Settings.ACTION_PRIVACY_SETTINGS, I18nR.string.search_android_privacy, BaseR.drawable.lock_24px, "privacy"),
            android(Settings.ACTION_INPUT_METHOD_SETTINGS, I18nR.string.search_android_keyboard, BaseR.drawable.keyboard_24px, "keyboard", "input", "ime"),
            android(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, I18nR.string.search_android_defaults, BaseR.drawable.apps_24px, "default", "apps"),
            android(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, I18nR.string.search_android_developer, BaseR.drawable.bug_report_24px, "developer", "usb", "debug"),
            telos("settings/appearance", I18nR.string.preference_screen_appearance, BaseR.drawable.palette_24px, "theme", "colors", "look"),
            telos("settings/wallpaper", I18nR.string.preference_screen_wallpaper, BaseR.drawable.wallpaper_24px, "wallpaper", "video"),
            telos("settings/icons", I18nR.string.preference_screen_icons, BaseR.drawable.apps_24px, "icons", "shapes"),
            telos("settings/search", I18nR.string.preference_screen_search, BaseR.drawable.search_24px, "search"),
            telos("settings/homescreen", I18nR.string.preference_screen_homescreen, BaseR.drawable.home_24px, "home", "grid", "dock"),
            telos("settings/gestures", I18nR.string.preference_screen_gestures, BaseR.drawable.gesture_24px, "gestures"),
            telos("settings/backup", I18nR.string.preference_screen_backup, BaseR.drawable.settings_backup_restore_24px, "backup", "restore"),
            telos("settings/integrations", I18nR.string.preference_screen_integrations, BaseR.drawable.apps_24px, "accounts", "plugins"),
            telos("settings/advanced", I18nR.string.preference_screen_advanced, BaseR.drawable.tune_24px, "advanced", "graphics", "vulkan"),
            telos("settings/about", I18nR.string.preference_screen_about, BaseR.drawable.info_24px, "about", "license"),
            telos("settings/debug", I18nR.string.preference_screen_debug, BaseR.drawable.bug_report_24px, "debug"),
            telos("settings/freeze", I18nR.string.preference_screen_freeze, BaseR.drawable.ac_unit_24px, "freeze"),
            telos("settings/floatinglauncher", I18nR.string.preference_screen_floating_launcher, BaseR.drawable.apps_24px, "sidebar", "floating"),
            telos("settings/dynamicisland", I18nR.string.preference_screen_dynamic_island, BaseR.drawable.notifications_24px, "island"),
            telos("settings/desktopmode", I18nR.string.preference_screen_desktop_mode, BaseR.drawable.home_24px, "desktop"),
            telos("settings/webapps", I18nR.string.preference_screen_web_app_shortcuts, BaseR.drawable.language_24px, "web", "pwa"),
            telos("settings/tags", I18nR.string.preference_screen_tags, BaseR.drawable.tag_24px, "tags"),
            telos("settings/hiddenitems", I18nR.string.preference_hidden_items, BaseR.drawable.visibility_off_24px, "hidden"),
            appSetting(SettingsDeepLinkContract.ROUTE_COMMS, "telos_phone_app", listOf("dialer", "call", "phone")),
            appSetting(SettingsDeepLinkContract.ROUTE_COMMS, "telos_messages_app", listOf("sms", "chat", "messages")),
            appSetting(SettingsDeepLinkContract.ROUTE_MEDIA, "telos_media_app", listOf("player")),
            appSetting(SettingsDeepLinkContract.ROUTE_RADIO, "telos_radio_app", listOf("fm", "radio")),
            appSetting(SettingsDeepLinkContract.ROUTE_MUSIC, "telos_music_app", listOf("songs")),
            appSetting(SettingsDeepLinkContract.ROUTE_VIDEO, "telos_video_app", listOf("movies")),
            appSetting(SettingsDeepLinkContract.ROUTE_TV, "telos_tv_app", listOf("tv", "television", "tileorasi", "τηλεόραση")),
            appSetting(SettingsDeepLinkContract.ROUTE_STREMIO, "telos_stremio_app", listOf("stremio", "addons", "στρέμιο")),
            appSetting(SettingsDeepLinkContract.ROUTE_PHOTOS, "telos_photos_app", listOf("gallery")),
            appSetting(SettingsDeepLinkContract.ROUTE_VIEWER, "telos_viewer_app", listOf("pdf", "documents")),
            appSetting(SettingsDeepLinkContract.ROUTE_FILES, "telos_files_app", listOf("files", "storage")),
            appSetting(SettingsDeepLinkContract.ROUTE_NETWORK, "telos_network_app", listOf("vpn", "firewall", "dns")),
            appSetting(SettingsDeepLinkContract.ROUTE_STORE, "telos_store_app", listOf("fdroid", "apk")),
            appSetting(SettingsDeepLinkContract.ROUTE_SYSTEM, "telos_system_app", listOf("shizuku", "appops")),
            appSetting(SettingsDeepLinkContract.ROUTE_WEATHER, "telos_weather_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_CALCULATOR, "telos_calculator_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_NOTES, "telos_notes_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_CAMERA, "telos_camera_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_CALENDAR, "telos_calendar_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_DOWNLOADS, "telos_downloads_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_ERMIS, "telos_ermis_app", listOf("mail", "email")),
            appSetting("settings/clock", "telos_clock_app"),
            appSetting("settings/magnifier", "telos_magnifier_app"),
            appSetting("settings/flashlight", "telos_flashlight_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_BACKUP, "telos_backup_app"),
            appSetting(SettingsDeepLinkContract.ROUTE_TRASH, "telos_trash_app"),
        )
    }
}
