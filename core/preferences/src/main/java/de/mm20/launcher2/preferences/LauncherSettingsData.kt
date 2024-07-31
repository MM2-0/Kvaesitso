package de.mm20.launcher2.preferences

import android.content.Context
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.search.SearchFilters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LauncherSettingsData internal constructor(
    val schemaVersion: Int = 2,

    val uiColorScheme: ColorScheme = ColorScheme.System,
    val uiTheme: ThemeDescriptor = ThemeDescriptor.Default,
    val uiCompatModeColors: Boolean = false,
    val uiFont: Font = Font.Outfit,
    val uiBaseLayout: BaseLayout = BaseLayout.PullDown,
    val uiOrientation: ScreenOrientation = ScreenOrientation.Auto,

    val wallpaperDim: Boolean = false,
    val wallpaperBlur: Boolean = true,
    val wallpaperBlurRadius: Int = 32,

    val mediaAllowList: Set<String> = emptySet(),
    val mediaDenyList: Set<String> = emptySet(),

    val clockWidgetCompact: Boolean = false,
    @Deprecated("")
    @SerialName("clockWidgetStyle")
    val _clockWidgetStyle: ClockWidgetStyle = ClockWidgetStyle.Digital1(),
    @SerialName("clockWidgetStyle2")
    internal val clockWidgetStyle: ClockWidgetStyleEnum = ClockWidgetStyleEnum.Digital1,
    val clockWidgetDigital1: ClockWidgetStyle.Digital1 = ClockWidgetStyle.Digital1(),
    val clockWidgetCustom: ClockWidgetStyle.Custom = ClockWidgetStyle.Custom(),
    val clockWidgetColors: ClockWidgetColors = ClockWidgetColors.Auto,
    val clockWidgetShowSeconds: Boolean = false,
    val clockWidgetUseThemeColor: Boolean = false,
    val clockWidgetAlarmPart: Boolean = true,
    val clockWidgetBatteryPart: Boolean = true,
    val clockWidgetMusicPart: Boolean = true,
    val clockWidgetDatePart: Boolean = true,
    val clockWidgetFillHeight: Boolean = true,
    val clockWidgetAlignment: ClockWidgetAlignment = ClockWidgetAlignment.Bottom,

    val homeScreenDock: Boolean = false,

    val favoritesEnabled: Boolean = true,
    val favoritesFrequentlyUsed: Boolean = true,
    val favoritesFrequentlyUsedRows: Int = 1,
    val favoritesEditButton: Boolean = true,

    val fileSearchProviders: Set<String> = setOf("local"),

    val contactSearchEnabled: Boolean = true,

    @Deprecated("Use calendarSearchProviders `local` instead")
    val calendarSearchEnabled: Boolean = true,
    val calendarSearchProviders: Set<String> = setOf("local"),

    val shortcutSearchEnabled: Boolean = true,

    val calculatorEnabled: Boolean = true,

    val unitConverterEnabled: Boolean = true,
    val unitConverterCurrencies: Boolean = true,

    val wikipediaSearchEnabled: Boolean = true,
    val wikipediaSearchImages: Boolean = true,
    val wikipediaCustomUrl: String? = null,

    val websiteSearchEnabled: Boolean = true,

    val badgesNotifications: Boolean = true,
    val badgesSuspendedApps: Boolean = true,
    val badgesCloudFiles: Boolean = true,
    val badgesShortcuts: Boolean = true,
    val badgesPlugins: Boolean = true,

    val gridColumnCount: Int = 5,
    val gridIconSize: Int = 48,
    val gridLabels: Boolean = true,

    val searchBarStyle: SearchBarStyle = SearchBarStyle.Transparent,
    val searchBarColors: SearchBarColors = SearchBarColors.Auto,
    val searchBarKeyboard: Boolean = true,
    val searchLaunchOnEnter: Boolean = true,
    val searchBarBottom: Boolean = false,
    val searchBarFixed: Boolean = false,

    val searchResultsReversed: Boolean = false,
    val searchResultOrder: SearchResultOrder = SearchResultOrder.Weighted,
    val separateWorkProfile: Boolean = true,

    val rankingWeightFactor: WeightFactor = WeightFactor.Default,

    val hiddenItemsShowButton: Boolean = false,

    val iconsShape: IconShape = IconShape.PlatformDefault,
    val iconsAdaptify: Boolean = false,
    val iconsThemed: Boolean = false,
    val iconsForceThemed: Boolean = false,
    val iconsPack: String? = null,
    @Deprecated("Use iconsThemed instead")
    val iconsPackThemed: Boolean = false,

    val easterEgg: Boolean = false,

    val systemBarsHideStatus: Boolean = false,
    val systemBarsHideNav: Boolean = false,
    val systemBarsStatusColors: SystemBarColors = SystemBarColors.Auto,
    val systemBarsNavColors: SystemBarColors = SystemBarColors.Auto,

    val surfacesOpacity: Float = 1f,
    val surfacesRadius: Int = 24,
    val surfacesBorderWidth: Int = 0,
    val surfacesShape: SurfaceShape = SurfaceShape.Rounded,

    val widgetsEditButton: Boolean = true,

    val gesturesSwipeDown: GestureAction = GestureAction.Notifications,
    val gesturesSwipeLeft: GestureAction = GestureAction.NoAction,
    val gesturesSwipeRight: GestureAction = GestureAction.NoAction,
    val gesturesDoubleTap: GestureAction = GestureAction.ScreenLock,
    val gesturesLongPress: GestureAction = GestureAction.NoAction,
    val gesturesHomeButton: GestureAction = GestureAction.NoAction,

    val animationsCharging: Boolean = true,

    val stateTagsMultiline: Boolean = false,

    val weatherProvider: String = "metno",
    val weatherAutoLocation: Boolean = true,
    val weatherLocation: LatLon? = null,
    val weatherLocationName: String? = null,
    val weatherLastLocation: LatLon? = null,
    val weatherLastUpdate: Long = 0L,
    val weatherProviderSettings: Map<String, ProviderSettings> = emptyMap(),
    val weatherImperialUnits: Boolean = false,

    @Deprecated("Use locationSearchProviders instead")
    val locationSearchEnabled: Boolean = false,
    val locationSearchProviders: Set<String> = setOf("openstreetmaps"),
    val locationSearchImperialUnits: Boolean = false,
    val locationSearchRadius: Int = 1500,
    val locationSearchHideUncategorized: Boolean = true,
    val locationSearchOverpassUrl: String? = null,
    val locationSearchTileServer: String? = null,
    val locationSearchShowMap: Boolean = true,
    val locationSearchShowPositionOnMap: Boolean = false,
    val locationSearchThemeMap: Boolean = true,

    val searchFilter: SearchFilters = SearchFilters(),
    val searchFilterBar: Boolean = true,
    val searchFilterBarItems: List<KeyboardFilterBarItem> = listOf(
        KeyboardFilterBarItem.OnlineResults,
        KeyboardFilterBarItem.Apps,
        KeyboardFilterBarItem.Shortcuts,
        KeyboardFilterBarItem.Events,
        KeyboardFilterBarItem.Contacts,
        KeyboardFilterBarItem.Files,
        KeyboardFilterBarItem.Articles,
        KeyboardFilterBarItem.Websites,
        KeyboardFilterBarItem.Places,
        KeyboardFilterBarItem.Tools,
        KeyboardFilterBarItem.HiddenResults,
    ),


    ) {
    constructor(
        context: Context,
    ) : this(
        weatherImperialUnits = context.resources.getBoolean(R.bool.default_imperialUnits),
        locationSearchImperialUnits = context.resources.getBoolean(R.bool.default_imperialUnits),
        gridColumnCount = context.resources.getInteger(R.integer.config_columnCount),
    )
}

@Serializable
enum class ColorScheme {
    Light,
    Dark,
    System,
}

@Serializable
enum class Font {
    Outfit,
    System,
}


@Serializable
sealed interface ThemeDescriptor {
    @Serializable
    @SerialName("default")
    data object Default : ThemeDescriptor

    @Serializable
    @SerialName("bw")
    data object BlackAndWhite : ThemeDescriptor

    @Serializable
    @SerialName("custom")
    data class Custom(
        val id: String,
    ) : ThemeDescriptor
}

internal enum class ClockWidgetStyleEnum {
    Digital1,
    Digital2,
    Orbit,
    Analog,
    Binary,
    Segment,
    Empty,
    Custom,
}

@Serializable
sealed interface ClockWidgetStyle {
    @Serializable
    @SerialName("digital1")
    data class Digital1(
        val outlined: Boolean = false,
        @Deprecated("Variant.MDY has been replaced with LauncherSettingsData.clockWidgetUseThemeColor")
        val variant: Variant = Variant.Default,
    ) : ClockWidgetStyle {
        @Serializable
        @Deprecated("No longer in use")
        enum class Variant {
            Default,
            MDY,
        }
    }

    @Serializable
    @SerialName("digital2")
    data object Digital2 : ClockWidgetStyle

    @Serializable
    @SerialName("orbit")
    data object Orbit : ClockWidgetStyle

    @Serializable
    @SerialName("analog")
    data object Analog : ClockWidgetStyle

    @Serializable
    @SerialName("binary")
    data object Binary : ClockWidgetStyle

    @Serializable
    @SerialName("segment")
    data object Segment : ClockWidgetStyle

    @Serializable
    @SerialName("empty")
    data object Empty : ClockWidgetStyle

    @Serializable
    @SerialName("custom")
    data class Custom(
        val widgetId: Int? = null,
        val width: Int? = null,
        val height: Int = 200,
    ) : ClockWidgetStyle
}

@Serializable
enum class ClockWidgetColors {
    Auto,
    Light,
    Dark,
}

@Serializable
enum class ClockWidgetAlignment {
    Top,
    Center,
    Bottom,
}

@Serializable
enum class SearchBarStyle {
    Transparent,
    Solid,
    Hidden,
}

@Serializable
enum class SearchBarColors {
    Auto,
    Light,
    Dark,
}

@Serializable
enum class IconShape {
    PlatformDefault,
    Circle,
    Square,
    RoundedSquare,
    Triangle,
    Squircle,
    Hexagon,
    Pentagon,
    Teardrop,
    Pebble,
    EasterEgg,
}

@Serializable
enum class SystemBarColors {
    Auto,
    Light,
    Dark,
}

@Serializable
enum class SurfaceShape {
    Rounded,
    Cut,
}

@Serializable
enum class BaseLayout {
    PullDown,
    Pager,
    PagerReversed,
}

@Serializable
enum class ScreenOrientation {
    Auto,
    Portrait,
    Landscape,
}

@Serializable
sealed interface GestureAction {
    @Serializable
    @SerialName("no_action")
    data object NoAction : GestureAction

    @Serializable
    @SerialName("notifications")
    data object Notifications : GestureAction

    @Serializable
    @SerialName("quick_settings")
    data object QuickSettings : GestureAction

    @Serializable
    @SerialName("screen_lock")
    data object ScreenLock : GestureAction

    @Serializable
    @SerialName("search")
    data object Search : GestureAction

    @Serializable
    @SerialName("power_menu")
    data object PowerMenu : GestureAction

    @Serializable
    @SerialName("recents")
    data object Recents : GestureAction

    @Serializable
    @SerialName("launch_searchable")
    data class Launch(val key: String?) : GestureAction
}

@Serializable
enum class SearchResultOrder {
    Weighted,
    Alphabetical,
    LaunchCount,
}

@Serializable
enum class WeightFactor {
    Default,
    Low,
    High,
}

@Serializable
data class LatLon(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class ProviderSettings(
    val locationId: String? = null,
    val locationName: String? = null,
    val managedLocation: Boolean = false,
)

@Serializable
enum class KeyboardFilterBarItem {
    @SerialName("online") OnlineResults,
    @SerialName("apps") Apps,
    @SerialName("websites") Websites,
    @SerialName("articles") Articles,
    @SerialName("places") Places,
    @SerialName("files") Files,
    @SerialName("shortcuts") Shortcuts,
    @SerialName("contacts") Contacts,
    @SerialName("events") Events,
    @SerialName("tools") Tools,
    @SerialName("hidden") HiddenResults,
}