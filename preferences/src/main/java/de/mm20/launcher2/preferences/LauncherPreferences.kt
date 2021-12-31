package de.mm20.launcher2.preferences

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager


class LauncherPreferences(val context: Application, version: Int = 3) {
    internal val preferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }


    var version by IntPreference("preferences_version", default = 1)

    init {
        val oldVersion = this.version
        if ( version >= 2 && oldVersion <= 1) {
            val appStartAnim = preferences.getInt("app_start_anim", 0)
            preferences.edit {
                remove("app_start_anim")
                putString("app_start_anim", appStartAnim.toString())
            }
        }
        if (version >= 3 && oldVersion <= 2) {
            val translucentCards = preferences.getBoolean("translucent_cards", false)
            val theme = preferences.getString("theme", "2")

            preferences.edit {
                remove("translucent_cards")
                putInt("card_opacity", if (translucentCards) 0xCC else 0xFF)
                if (theme == "4") {
                    putString("theme", "1")
                    putString("card_background", "2")
                }
            }
        }
        if (version >= 4 && oldVersion <= 3) {
            val appStartAnim = preferences.getString("app_start_anim", "5")
            if (appStartAnim == "0" || appStartAnim == "1")
            preferences.edit {
                putString("app_start_anim", "2")
            }
        }
        this.version = version
    }


    var theme by EnumPreference("theme", default = Themes.SYSTEM)
    var lightStatusBar by BooleanPreference("light_status_bar", default = false)
    var lightNavBar by BooleanPreference("light_nav_bar", default = false)
    var dimWallpaper by BooleanPreference("dim_wallpaper", default = false)
    var appStartAnim by EnumPreference("app_start_anim", default = AppStartAnimation.M)

    var searchShowFavorites by BooleanPreference("search_show_favorites", default = true)
    var searchAutoAddFavorites by BooleanPreference("search_auto_add_favorites", default = true)
    var searchCalculator by BooleanPreference("search_calculator", default = true)
    var searchUnitConverter by BooleanPreference("search_unitconverter", default = true)
    var searchFiles by BooleanPreference("search_files", default = true)
    var searchWikipedia by BooleanPreference("search_wikipedia", default = true)
    var searchWikipediaMobileData by BooleanPreference("search_wikipedia_mobile_data", default = true)
    var searchWebsite by BooleanPreference("search_websites", default = true)
    var searchWebsitesProtocol by EnumPreference("search_websites_protocol", default = WebsiteProtocols.HTTPS)
    var searchWebsitesMobileData by BooleanPreference("search_websearch_mobile_data", default = true)
    var searchActivities by BooleanPreference("search_activities", default = true)
    var searchCalendars by BooleanPreference("search_calendars", default = true)
    var searchContacts by BooleanPreference("search_contacts", default = true)
    var searchWikipediaPictures by BooleanPreference("search_wikipedia_pictures", default = false)
    var searchOwncloud by BooleanPreference("search_owncloud", default = false)
    var searchNextcloud by BooleanPreference("search_nextcloud", default = false)
    var searchOneDrive by BooleanPreference("search_onedrive", default = false)
    var searchGDrive by BooleanPreference("search_gdrive", default = false)
    var searchGDriveMobileData by BooleanPreference("search_gdrive_mobile_data", default = false)

    var notificationBadges by BooleanPreference("notification_badges", default = true)
    var cloudBadges by BooleanPreference("cloud_badges", default = true)
    var suspendBadges by BooleanPreference("suspended_badges", default = true)
    var profileBadges by BooleanPreference("profile_badges", default = true)
    var shortcutBadges by BooleanPreference("shortcut_badges", default = true)

    var calendarMaxEvents by StringPreference("calendar_max_events", default = "10")

    var themedIcons by BooleanPreference("themed_icons", default = false)
    var legacyIconBg by StringPreference("legacy_icon_bg", default = "1")
    var blurCards by BooleanPreference("blur_cards", default = false)
    var searchStyle by EnumPreference("search_style", default = SearchStyles.NO_BG)
    var imperialUnits by BooleanPreference("imperial_units", default = context.resources.getBoolean(R.bool.default_imperialUnits))
    var translucentCards by BooleanPreference("translucent_cards", default = false)
    var iconShape by EnumPreference("icon_shape", default = IconShape.PLATFORM_DEFAULT)
    var firstRunVersion by IntPreference("first_run_version", default = 0)

    var colorScheme by EnumPreference("card_background", default = ColorSchemes.DEFAULT)
    var cardOpacity by IntPreference("card_opacity", default = 0xFF)
    var cardStrokeWidth by IntPreference("card_stroke_width", default = 0)
    var cardRadius by IntPreference("card_radius", default = 8)

    var wallpaperColor by IntPreference("wallpaper_color", default = 0x0)
    var isLightWallpaper by BooleanPreference("is_light_wallpaper", default = false)

    var weatherProvider by EnumPreference("weather_provider", default = WeatherProviders.MET_NO)

    var unselectedCalendars by LongListPreference("unselected_calendars", default = emptyList())
    var calendarHideAllday by BooleanPreference("calendar_hide_allday", default = false)

    var hasRequestedNotificationPermission by BooleanPreference("requested_notification_permission", default = false)

    var easterEggEnabled by BooleanPreference("easter_egg", default = false)

    var gridColumnCount by IntPreference("grid_column_count", default = context.resources.getInteger(R.integer.config_columnCount))


    fun doOnPreferenceChange(vararg keys: String, action: (String) -> Unit): () -> Unit {
        val listener =  { _: SharedPreferences, key: String ->
            if (keys.contains(key)) action(key)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    companion object {
        lateinit var instance: LauncherPreferences
        fun initialize(app: Application) {
            instance = LauncherPreferences(app)
        }
    }
}

enum class WebsiteProtocols(override val value: String) : PreferenceEnum {
    HTTP("0"),
    HTTPS("1")
}

enum class Themes(override val value: String) : PreferenceEnum {
    SYSTEM("2"),
    LIGHT("0"),
    DARK("1"),
    AUTO("3");

    companion object {
        fun byValue(value: String): Themes {
            return values().first { it.value == value }
        }
    }
}

enum class SearchStyles(override val value: String) : PreferenceEnum {
    NO_BG("0"),
    SOLID("1"),
    HIDDEN("2"),
}

enum class IconShape(override val value: String) : PreferenceEnum {
    CIRCLE("0"),
    SQUARE("1"),
    ROUNDED_SQUARE("2"),
    TRIANGLE("3"),
    SQUIRCLE("4"),
    HEXAGON("5"),
    HEART("6"),
    PENTAGON("7"),
    PLATFORM_DEFAULT("8")
}

enum class WeatherProviders(override val value: String) : PreferenceEnum {
    OPENWEATHERMAP("0"),
    HERE("3"),
    MET_NO("2"),
    BRIGHT_SKY("4");

    companion object {
        fun byValue(value: String): WeatherProviders {
            return values().first { it.value == value }
        }
    }
}

enum class ColorSchemes(override val value: String) : PreferenceEnum {
    DEFAULT("0"),
    BLACK("2");
    companion object {
        fun byValue(value: String): ColorSchemes {
            return values().first { it.value == value }
        }
    }
}

enum class AppStartAnimation(override val value: String): PreferenceEnum {
    DEFAULT("5"),
    M("2"),
    FADE("3"),
    SLIDE_BOTTOM("4")
}