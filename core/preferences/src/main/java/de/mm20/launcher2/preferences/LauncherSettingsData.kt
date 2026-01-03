package de.mm20.launcher2.preferences

import android.content.Context
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.UUID

@Serializable
@ConsistentCopyVisibility
data class LauncherSettingsData internal constructor(
    val schemaVersion: Int = 5,

    val uiColorScheme: ColorScheme = ColorScheme.System,
    @Serializable(with = UUIDSerializer::class)
    val uiColorsId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiShapesId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiTransparenciesId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiTypographyId: UUID = UUID(0L, 0L),

    val uiCompatModeColors: Boolean = false,
    val uiFont: Font = Font.Outfit,
    @Deprecated("No longer in use, only used for migration")
    val uiBaseLayout: BaseLayout = BaseLayout.PullDown,
    val uiOrientation: ScreenOrientation = ScreenOrientation.Auto,

    val wallpaperDim: Boolean = false,
    val wallpaperBlur: Boolean = true,
    val wallpaperBlurRadius: Int = 32,

    val mediaAllowList: Set<String> = emptySet(),
    val mediaDenyList: Set<String> = emptySet(),

    val clockWidgetCompact: Boolean = false,
    val clockWidgetSmartspacer: Boolean = false,

    @Deprecated("")
    @SerialName("clockWidgetStyle")
    val _clockWidgetStyle: ClockWidgetStyle = ClockWidgetStyle.Digital1(),
    @SerialName("clockWidgetStyle2")
    internal val clockWidgetStyle: ClockWidgetStyleEnum = ClockWidgetStyleEnum.Digital1,
    val clockWidgetDigital1: ClockWidgetStyle.Digital1 = ClockWidgetStyle.Digital1(),
    val clockWidgetAnalog: ClockWidgetStyle.Analog = ClockWidgetStyle.Analog(),
    val clockWidgetCustom: ClockWidgetStyle.Custom = ClockWidgetStyle.Custom(),
    val clockWidgetColors: ClockWidgetColors = ClockWidgetColors.Auto,
    val clockWidgetShowSeconds: Boolean = false,
    val clockWidgetMonospaced: Boolean = false,
    val clockWidgetUseThemeColor: Boolean = false,
    val clockWidgetAlarmPart: Boolean = true,
    val clockWidgetBatteryPart: Boolean = true,
    val clockWidgetMusicPart: Boolean = true,
    val clockWidgetDatePart: Boolean = true,
    val clockWidgetFillHeight: Boolean = false,
    val clockWidgetAlignment: ClockWidgetAlignment = ClockWidgetAlignment.Bottom,

    val homeScreenDock: Boolean = false,
    val homeScreenDockRows: Int = 1,
    val homeScreenWidgets: Boolean = false,

    val favoritesEnabled: Boolean = true,
    val favoritesFrequentlyUsed: Boolean = true,
    val favoritesFrequentlyUsedRows: Int = 1,
    val favoritesEditButton: Boolean = true,
    val favoritesCompactTags: Boolean = false,

    val searchAllApps: Boolean = true,

    val fileSearchProviders: Set<String> = setOf("local"),

    @Deprecated("Use contactSearchProviders `local` instead")
    val contactSearchEnabled: Boolean = true,
    val contactSearchProviders: Set<String> = setOf("local"),
    val contactSearchCallOnTap: Boolean = false,

    @Deprecated("Use calendarSearchProviders `local` instead")
    val calendarSearchEnabled: Boolean = true,
    val calendarSearchProviders: Set<String> = setOf("local"),
    val calendarSearchExcludedCalendars: Set<String> = setOf(),

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
    val gridList: Boolean = false,
    val gridListIcons: Boolean = true,

    val searchBarStyle: SearchBarStyle = SearchBarStyle.Transparent,
    val searchBarColors: SearchBarColors = SearchBarColors.Auto,
    val searchBarKeyboard: Boolean = true,
    val searchLaunchOnEnter: Boolean = true,
    val searchBarBottom: Boolean = false,
    val searchBarFixed: Boolean = false,

    val searchResultsReversed: Boolean = false,
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
    @Deprecated("Replaces with shape schemes")
    val surfacesRadius: Int = 24,
    val surfacesBorderWidth: Int = 0,
    @Deprecated("Replaces with shape schemes")
    val surfacesShape: SurfaceShape = SurfaceShape.Rounded,

    val widgetsEditButton: Boolean = true,

    val gesturesSwipeDown: GestureAction = GestureAction.Search,
    val gesturesSwipeLeft: GestureAction = GestureAction.NoAction,
    val gesturesSwipeRight: GestureAction = GestureAction.NoAction,
    val gesturesSwipeUp: GestureAction = GestureAction.Widgets,
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

    @Deprecated("Use locationSearchProviders instead")
    val locationSearchEnabled: Boolean = false,
    val locationSearchProviders: Set<String> = setOf("openstreetmaps"),
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


    @JsonNames("clockWidgetTimeFormat")
    val localeTimeFormat: TimeFormat = TimeFormat.System,
    val localeMeasurementSystem: MeasurementSystem = MeasurementSystem.System,
    /**
     * The ID of the transliterator to use. The empty string means to pick a transliterator
     * automatically. null disables the transliterator.
     */
    val localeTransliterator: String? = "",

    val feedProviderPackage: String? = null


    ) {
    constructor(
        context: Context,
    ) : this(
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
    data class Analog(
        val showTicks: Boolean = false
    ) : ClockWidgetStyle

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
    @SerialName("widgets")
    data object Widgets : GestureAction

    @Serializable
    @SerialName("power_menu")
    data object PowerMenu : GestureAction

    @Serializable
    @SerialName("recents")
    data object Recents : GestureAction

    @Serializable
    @SerialName("launch_searchable")
    data class Launch(val key: String?) : GestureAction

    @Serializable
    @SerialName("feed")
    data object Feed : GestureAction
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

@Serializable
enum class TimeFormat {
    @SerialName("system") System,
    @SerialName("12h") TwelveHour,
    @SerialName("24h") TwentyFourHour
}


@Serializable
enum class MeasurementSystem {
    @SerialName("system") System,
    @SerialName("metric") Metric,
    @SerialName("uk") UnitedKingdom,
    @SerialName("us") UnitedStates,
}