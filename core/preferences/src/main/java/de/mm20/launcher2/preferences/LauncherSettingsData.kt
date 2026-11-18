package de.mm20.launcher2.preferences

import android.content.Context
import de.mm20.launcher2.base.R
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.serialization.ColorIntAsHexSerializer
import de.mm20.launcher2.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.UUID

/**
 * The full set of launcher settings, as read from / written to disk.
 *
 * This used to be one flat data class with every setting as a top-level constructor property.
 * Once that list grew past ~250 properties, the compiler-generated `copy()` method exceeded the
 * JVM's 255-word-per-method parameter limit (each property is a word, wide types like Long are
 * two, plus the bitmask ints Kotlin generates for `copy`'s default arguments) - kotlinc/d8 didn't
 * catch it, but ART's bytecode verifier rejected the class at runtime on first use, crashing the
 * app on launch. Properties are now grouped into nested data classes by feature area, each with
 * its own small `copy()`, well under the limit.
 *
 * The on-disk JSON format is unaffected by this grouping - see [LauncherSettingsDataKSerializer],
 * which flattens/unflattens so every field keeps living at the JSON top level exactly as before.
 * This means existing users' settings files keep working without a migration.
 */
@Serializable(with = LauncherSettingsDataKSerializer::class)
@ConsistentCopyVisibility
data class LauncherSettingsData internal constructor(
    val schemaVersion: Int = 12,

    val ui: UiGroup = UiGroup(),
    val wallpaper: WallpaperGroup = WallpaperGroup(),
    val media: MediaGroup = MediaGroup(),
    val clock: ClockGroup = ClockGroup(),
    val home: HomeGroup = HomeGroup(),
    val favorites: FavoritesGroup = FavoritesGroup(),
    val appSearch: AppSearchGroup = AppSearchGroup(),
    val fileSearch: FileSearchGroup = FileSearchGroup(),
    val contactSearch: ContactSearchGroup = ContactSearchGroup(),
    val calendarSearch: CalendarSearchGroup = CalendarSearchGroup(),
    val shortcutSearch: ShortcutSearchGroup = ShortcutSearchGroup(),
    val calculator: CalculatorGroup = CalculatorGroup(),
    val unitConverter: UnitConverterGroup = UnitConverterGroup(),
    val wikipedia: WikipediaGroup = WikipediaGroup(),
    val website: WebsiteGroup = WebsiteGroup(),
    val badges: BadgesGroup = BadgesGroup(),
    val grid: GridGroup = GridGroup(),
    val searchBar: SearchBarGroup = SearchBarGroup(),
    val searchResults: SearchResultsGroup = SearchResultsGroup(),
    val icons: IconsGroup = IconsGroup(),
    val misc: MiscGroup = MiscGroup(),
    val systemBars: SystemBarsGroup = SystemBarsGroup(),
    val surfaces: SurfacesGroup = SurfacesGroup(),
    val widgets: WidgetsGroup = WidgetsGroup(),
    val gestures: GesturesGroup = GesturesGroup(),
    val videoWallpaper: VideoWallpaperGroup = VideoWallpaperGroup(),
    val performance: PerformanceGroup = PerformanceGroup(),
    val shutters: ShuttersGroup = ShuttersGroup(),
    val animations: AnimationsGroup = AnimationsGroup(),
    val stateTags: StateTagsGroup = StateTagsGroup(),
    val weather: WeatherGroup = WeatherGroup(),
    val locationSearch: LocationSearchGroup = LocationSearchGroup(),
    val searchFilterGroup: SearchFilterGroup = SearchFilterGroup(),
    val locale: LocaleGroup = LocaleGroup(),
    val feed: FeedGroup = FeedGroup(),
    val freeze: FreezeGroup = FreezeGroup(),
    val protection: ProtectionGroup = ProtectionGroup(),
    val appLock: AppLockGroup = AppLockGroup(),
    val floatingLauncher: FloatingLauncherGroup = FloatingLauncherGroup(),
    val dynamicIsland: DynamicIslandGroup = DynamicIslandGroup(),
    val webAppsPanel: WebAppsPanelGroup = WebAppsPanelGroup(),
    val webAppBrowsing: WebAppBrowsingGroup = WebAppBrowsingGroup(),
    val contextProfiles: ContextProfilesGroup = ContextProfilesGroup(),
    val desktopMode: DesktopModeGroup = DesktopModeGroup(),
    val agents: AgentsGroup = AgentsGroup(),
    val tweaks: TweaksGroup = TweaksGroup(),
    val battery: BatteryGroup = BatteryGroup(),
    // === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
    val comms: CommsGroup = CommsGroup(),
    // === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
) {
    constructor(
        context: Context,
    ) : this(
        grid = GridGroup(gridColumnCount = context.resources.getInteger(R.integer.config_columnCount)),
    )
}

@Serializable
data class UiGroup(
    val uiColorScheme: ColorScheme = ColorScheme.System,
    /** Hour of day [0,23] when the dark scheme starts, used only by [ColorScheme.Time]. */
    val uiColorSchemeNightStart: Int = 20,
    /** Hour of day [0,23] when the light scheme starts, used only by [ColorScheme.Time]. */
    val uiColorSchemeDayStart: Int = 7,
    @Serializable(with = UUIDSerializer::class)
    val uiColorsId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiShapesId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiTransparenciesId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiTypographyId: UUID = UUID(0L, 0L),
    /** Independent text-size multiplier for launcher UI, applied on top of the system font scale. */
    val uiFontScale: Float = 1f,
    val uiCompatModeColors: Boolean = false,
    @Deprecated("No longer in use, only used for migration")
    val uiBaseLayout: BaseLayout = BaseLayout.PullDown,
    val uiOrientation: ScreenOrientation = ScreenOrientation.Auto,
    /** Switch the color theme to AMOLED while Battery Saver is on. */
    val uiAmoledOnPowerSave: Boolean = false,
    val uiAmoledPowerSaveEngaged: Boolean = false,
    @Serializable(with = UUIDSerializer::class)
    val uiColorsIdBeforePowerSave: UUID = UUID(0L, 0L),
    /** Home-screen System widget (Vulkan + Network). Off until enabled in Telos System. */
    val systemWidgetEnabled: Boolean = false,
)

@Serializable
data class WallpaperGroup(
    val wallpaperTextColors: SystemBarColors = SystemBarColors.Auto,
    val wallpaperDim: Boolean = false,
    val wallpaperBlur: Boolean = true,
    val wallpaperBlurRadius: Int = 32,
)

@Serializable
data class MediaGroup(
    val mediaAllowList: Set<String> = emptySet(),
    val mediaDenyList: Set<String> = emptySet(),
)

@Serializable
@ConsistentCopyVisibility
data class ClockGroup internal constructor(
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
    @Deprecated("")
    @SerialName("clockWidgetBatteryPart")
    val _clockWidgetBatteryPart: Boolean = true,
    @SerialName("clockWidgetBatteryPart2")
    val clockWidgetBatteryPart: BatteryStatusVisibility = BatteryStatusVisibility.Show,
    val clockWidgetMusicPart: Boolean = true,
    val clockWidgetDatePart: Boolean = true,
    val clockWidgetWeatherPart: Boolean = false,
    val clockWidgetFillHeight: Boolean = false,
    val clockWidgetAlignment: ClockWidgetAlignment = ClockWidgetAlignment.Bottom,
)

@Serializable
data class HomeGroup(
    val homeScreenDock: Boolean = false,
    val homeScreenDockRows: Int = 1,
    val homeScreenDockColumns: Int = 5,
    val homeScreenDockDefaultPage: Int = 0,
    val homeScreenWidgets: Boolean = false,
    val widgetsTutorialShown: Boolean = false,
    /** False until the user accepts or skips the one-time Help intro on the home screen. */
    val helpIntroShown: Boolean = false,
    /** 1 = the single, original home screen (default, matches all prior behavior). Up to 9
     * enables extra swipeable pages to the right, each an independent widget area. */
    val homeScreenPageCount: Int = 1,
    val homeScreenDockPages: List<List<DockItem>> = emptyList(),
    /** Whether the dock has already been auto-seeded with default system apps on first launch.
     * Set once and never reset, so a dock the user deliberately emptied out afterwards isn't
     * re-populated against their wishes. */
    val homeScreenDockAutoPopulated: Boolean = false,
)

@Serializable
data class FavoritesGroup(
    val favoritesEnabled: Boolean = true,
    val favoritesFrequentlyUsed: Boolean = true,
    val favoritesFrequentlyUsedRows: Int = 1,
    val favoritesEditButton: Boolean = true,
    val favoritesCompactTags: Boolean = false,
)

@Serializable
enum class DrawerStyle {
    Classic,
    CyberMatrix,
    HoloPortal,
    CommandDeck,
}

@Serializable
data class AppSearchGroup(
    val searchAllApps: Boolean = true,
    val appsShowDetails: Boolean = true,
    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
    val moveFrozenAppsToEnd: Boolean = false,
    val drawerStyle: DrawerStyle = DrawerStyle.Classic,
    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
)

@Serializable
data class FileSearchGroup(
    val fileSearchProviders: Set<String> = setOf("local"),
    val fileSearchDocuments: Boolean = true,
    val fileSearchImages: Boolean = true,
    val fileSearchVideos: Boolean = true,
    val fileSearchMusic: Boolean = true,
    val fileSearchOther: Boolean = true,
    val fileSearchExcludedFolders: Set<String> = emptySet(),
)

@Serializable
data class ContactSearchGroup(
    @Deprecated("Use contactSearchProviders `local` instead")
    val contactSearchEnabled: Boolean = true,
    val contactSearchProviders: Set<String> = setOf("local"),
    val contactSearchCallOnTap: Boolean = false,
)

@Serializable
data class CalendarSearchGroup(
    @Deprecated("Use calendarSearchProviders `local` instead")
    val calendarSearchEnabled: Boolean = true,
    val calendarSearchProviders: Set<String> = setOf("local"),
    val calendarSearchExcludedCalendars: Set<String> = setOf(),
)

@Serializable
data class ShortcutSearchGroup(
    val shortcutSearchEnabled: Boolean = true,
)

@Serializable
data class CalculatorGroup(
    val calculatorEnabled: Boolean = true,
)

@Serializable
data class UnitConverterGroup(
    val unitConverterEnabled: Boolean = true,
    val unitConverterCurrencies: Boolean = true,
)

@Serializable
data class WikipediaGroup(
    val wikipediaSearchEnabled: Boolean = true,
    val wikipediaSearchImages: Boolean = true,
    val wikipediaCustomUrl: String? = null,
)

@Serializable
data class WebsiteGroup(
    val websiteSearchEnabled: Boolean = true,
)

@Serializable
data class BadgesGroup(
    val badgesNotifications: Boolean = true,
    val badgesSuspendedApps: Boolean = true,
    val badgesCloudFiles: Boolean = true,
    val badgesShortcuts: Boolean = true,
    val badgesPlugins: Boolean = true,
    val badgesNotificationStyle: NotificationBadgeStyle = NotificationBadgeStyle.Dot,
    /** Null = follow the theme's tertiary color (the pre-existing default look). */
    @Serializable(with = ColorIntAsHexSerializer::class)
    val badgesNotificationColor: Int? = null,
)

@Serializable
data class GridGroup(
    val gridColumnCount: Int = 5,
    val gridIconSize: Int = 48,
    val gridLabels: Boolean = true,
    val gridLabelSize: Float = 12f,
    val gridLabelMaxLines: Int = 1,
    val gridLabelShadow: Boolean = false,
    @Serializable(with = ColorIntAsHexSerializer::class)
    val gridLabelColor: Int? = null,
    val gridList: Boolean = false,
    val gridListIcons: Boolean = true,
    val homeGridColumnCount: Int? = null,
    val homeGridIconSize: Int? = null,
    val drawerGridColumnCount: Int? = null,
    val drawerGridIconSize: Int? = null,
    val dockGridColumnCount: Int? = null,
    val dockGridIconSize: Int? = null,
    val desktopLocked: Boolean = false,
    val dockBackgroundEnabled: Boolean = false,
    @Serializable(with = ColorIntAsHexSerializer::class)
    val dockBackgroundColor: Int? = null,
    val dockBackgroundOpacity: Float = 0.3f,
    val dockBackgroundBlur: Int = 0,
    val dockBackgroundShadow: Int = 0,
    val drawerFullWidth: Boolean = false,
    val drawerBackgroundEnabled: Boolean = true,
    @Serializable(with = ColorIntAsHexSerializer::class)
    val drawerBackgroundColor: Int? = null,
    val drawerBackgroundOpacity: Float = 0.35f,
    val drawerBackgroundBlur: Int = 32,
    /** Glass widgets use the wallpaper blur and translucent cards. */
    val widgetsGlassBackground: Boolean = true,
    @Serializable(with = ColorIntAsHexSerializer::class)
    val folderBackgroundColor: Int? = null,
    val dockPageIndicatorEnabled: Boolean = true,
    @Serializable(with = ColorIntAsHexSerializer::class)
    val dockPageIndicatorColor: Int? = null,
    /** Shows the folder's first item's icon as a fading "cover" overlay while the folder
     * popup is opening/closing, mimicking a lid that lifts away to reveal the folder's contents. */
    val folderCoverEnabled: Boolean = true,
)

@Serializable
data class SearchBarGroup(
    val searchBarStyle: SearchBarStyle = SearchBarStyle.Transparent,
    val searchBarColors: SearchBarColors = SearchBarColors.Auto,
    /** Override for searchBarColors while the drawer/search overlay is open. Null = use
     * searchBarColors for both the dock (home screen) and drawer contexts, same as before. */
    val searchBarColorsDrawer: SearchBarColors? = null,
    val searchBarKeyboard: Boolean = true,
    /** Applies IME_FLAG_NO_PERSONALIZED_LEARNING to text fields across the launcher (search,
     * web app shortcut editing, etc.) so the keyboard doesn't learn from or store typed text. */
    val privateKeyboard: Boolean = false,
    val searchLaunchOnEnter: Boolean = true,
    val searchBarBottom: Boolean = true,
    val searchBarFixed: Boolean = false,
    /** If true, the drawer/search results list keeps its scroll position when closed and
     * reopened, instead of always resetting to the top. */
    val searchRememberScrollPosition: Boolean = false,
)

@Serializable
data class SearchResultsGroup(
    val searchResultsReversed: Boolean = false,
    val separateWorkProfile: Boolean = true,
    val rankingWeightFactor: WeightFactor = WeightFactor.Default,
    val hiddenItemsShowButton: Boolean = false,
    /** Telos Radio: show the favorite stations that match the query as search results */
    val radioStationsInSearch: Boolean = true,
    /** Telos Media TV: show favorite, custom and recent TV channels that match the query as search results */
    val tvChannelsInSearch: Boolean = true,
    val searchAndroidSettings: Boolean = true,
    val searchTelosSettings: Boolean = true,
    val searchTelosAppSettings: Boolean = true,
)

@Serializable
data class IconsGroup(
    val iconsShape: IconShape = IconShape.PlatformDefault,
    val iconsAdaptify: Boolean = false,
    val iconsThemed: Boolean = false,
    val iconsForceThemed: Boolean = false,
    val iconsCyberpunk: Boolean = false,
    val iconsPack: String? = null,
    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
    val fallbackIconPacks: List<String> = emptyList(),
    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
    @Deprecated("Use iconsThemed instead")
    val iconsPackThemed: Boolean = false,
)

@Serializable
data class ArtBenchmarkRecord(
    val beforeMs: Long? = null,
    val afterMs: Long? = null,
    val waitBeforeMs: Long? = null,
    val waitAfterMs: Long? = null,
    val profileAfter: String? = null,
    val updatedAt: Long = 0L,
)

@Serializable
data class MiscGroup(
    val easterEgg: Boolean = false,
    /** Local crash reporter switch. Mirrored into SharedPreferences for the crash handler. */
    val crashReporterEnabled: Boolean = true,
    /** Retry root/previously paired wireless startup after unlock or launcher foreground. */
    val shizukuAutoStart: Boolean = false,
    val shizukuDisableWirelessAfterStart: Boolean = false,
    /** Slide/scale when changing screens inside Telos apps. Off by default. */
    val virtualAppPageEffects: Boolean = false,
    val artOptimizerMasterEnabled: Boolean = false,
    val artOptimizationProfile: String = "speed-profile",
    val artOptimizationScheduled: Boolean = false,
    val artOptimizationFrequencyDays: Long = 1,
    val artOptimizationRequireCharging: Boolean = true,
    val artOptimizationRequireIdle: Boolean = true,
    val artOptimizationRequireBatteryNotLow: Boolean = true,
    val artOptimizationSkipPowerSave: Boolean = true,
    val artOptimizationStorePostInstall: Boolean = false,
    /** Default false: frozen apps are optimized too so launcher unfreeze-to-launch stays instant. */
    val artSkipFrozenApps: Boolean = false,
    /** Sub-mode when [artSkipFrozenApps] is true: "skip" or "strip_reset". */
    val artFrozenAppsMode: String = "skip",
    /** Optional SQLite VACUUM & REINDEX pass. Default false. */
    val artSqliteOptimizeEnabled: Boolean = false,
    /** Automatically upgrade speed-profile -> speed when primary.prof/baseline profile is empty. */
    val artFallbackEmptyProfileToSpeed: Boolean = true,
    /** Saved cold-start benchmark records keyed by package name. */
    val artBenchmarkRecords: Map<String, ArtBenchmarkRecord> = emptyMap(),
)

@Serializable
data class SystemBarsGroup(
    val systemBarsHideStatus: Boolean = false,
    val systemBarsHideNav: Boolean = false,
    val systemBarsStatusColors: SystemBarColors = SystemBarColors.Auto,
    val systemBarsNavColors: SystemBarColors = SystemBarColors.Auto,
)

@Serializable
data class SurfacesGroup(
    val surfacesOpacity: Float = 1f,
    @Deprecated("Replaces with shape schemes")
    val surfacesRadius: Int = 24,
    val surfacesBorderWidth: Int = 0,
    @Deprecated("Replaces with shape schemes")
    val surfacesShape: SurfaceShape = SurfaceShape.Rounded,
)

@Serializable
data class WidgetsGroup(
    val widgetsEditButton: Boolean = true,
    val widgetScreenCount: Int = 1,
)

@Serializable
data class GesturesGroup(
    val gesturesSwipeDown: GestureAction = GestureAction.Search,
    val gesturesSwipeLeft: GestureAction = GestureAction.NoAction,
    // Swipe right reaches the Web Apps Panel by default - the panel is otherwise undiscoverable,
    // since there's no separate enabled/direction preference (see WebAppsPanelGroup).
    val gesturesSwipeRight: GestureAction = GestureAction.WebAppsPanel,
    val gesturesSwipeUp: GestureAction = GestureAction.Widgets(),
    val gesturesDoubleTap: GestureAction = GestureAction.ScreenLock,
    val gesturesLongPress: GestureAction = GestureAction.HomeScreenMenu,
    val gesturesHomeButton: GestureAction = GestureAction.NoAction,
    val gesturesPinchIn: GestureAction = GestureAction.NoAction,
    val gesturesPinchOut: GestureAction = GestureAction.NoAction,
    val gesturesTwoFingerSwipeUp: GestureAction = GestureAction.NoAction,
    val gesturesTwoFingerSwipeDown: GestureAction = GestureAction.NoAction,
    /** Off by default. Edge-swipe back/home/recents drawn by the accessibility service. */
    val telosNavigation: Boolean = false,
    /** Off by default. Telos recents list instead of the system Recents. */
    val telosRecents: Boolean = false,
)

@Serializable
data class VideoWallpaperGroup(
    val videoWallpaperPauseOnBatterySaver: Boolean = true,
    val videoWallpaperPauseOnThermalThrottling: Boolean = true,
    val videoWallpaperScalingMode: VideoWallpaperScalingMode = VideoWallpaperScalingMode.Fill,
    val videoWallpaperZoom: Float = 1f,
    val videoWallpaperPositionX: Float = 0f,
    val videoWallpaperPositionY: Float = 0f,
    val videoWallpaperBrightness: Float = 1f,
    val videoWallpaperSpeed: Float = 1f,
    val videoWallpaperStartBehavior: VideoWallpaperStartBehavior = VideoWallpaperStartBehavior.Resume,
    val videoWallpaperParallax: Boolean = false,
    val videoWallpaperParallaxStrength: Float = 0.2f,
    val videoWallpaperThemeColors: Boolean = true,
    /**
     * Off by default: the video wallpaper's WallpaperService is tied to the default display and
     * physically cannot render on the external display desktop mode uses, but the phone screen
     * itself keeps showing it while desktop mode is active on an external display, so pausing it
     * is a resource-saving choice the user should opt into rather than something sprung on them.
     */
    val videoWallpaperPauseOnDesktopMode: Boolean = false,
    /** User asked Telos to keep the video/GIF live wallpaper after APK updates. */
    val liveWallpaperWanted: Boolean = false,
)

@Serializable
data class PerformanceGroup(
    val performanceReduceAnimations: Boolean = false,
    /** Multiplies the duration of tween-based (fade/effects) motion specs. 1.0 = default. */
    val performanceAnimationSpeed: Float = 1f,
    /** 0 = no debounce (search fires on every keystroke, the historical behavior). */
    val performanceSearchDebounceMs: Int = 0,
    val performanceIconCacheSize: Int = 200,
    /** Spring dampingRatio for gesture release/back animations. 1f = no bounce (default,
     * matches Compose's Spring.DampingRatioNoBouncy), lower values overshoot before settling. */
    val performanceBouncePhysics: Float = 1f,
)

@Serializable
data class ShuttersGroup(
    val shuttersEnabled: Boolean = true,
    /** Package name -> the assigned shutter's SavableSearchable key (an app, shortcut, etc). */
    val shutterApps: Map<String, String> = emptyMap(),
)

@Serializable
data class AnimationsGroup(
    val animationsCharging: Boolean = true,
)

@Serializable
data class StateTagsGroup(
    val stateTagsMultiline: Boolean = false,
)

@Serializable
data class WeatherGroup(
    val weatherProvider: String = "openmeteo",
    val weatherAutoLocation: Boolean = true,
    val weatherLocation: LatLon? = null,
    val weatherLocationName: String? = null,
    val weatherLastLocation: LatLon? = null,
    val weatherLastUpdate: Long = 0L,
    val weatherProviderSettings: Map<String, ProviderSettings> = emptyMap(),
    /** Notifications for severe weather in the next hours, off by default */
    val weatherAlertsEnabled: Boolean = false,
    val weatherAlertRain: Boolean = true,
    /** Chance of rain in percent from which a notification is shown */
    val weatherAlertRainProbability: Int = 70,
    val weatherAlertHeavyRain: Boolean = true,
    val weatherAlertSnow: Boolean = true,
    val weatherAlertThunder: Boolean = true,
    val weatherAlertHeat: Boolean = true,
    /** Temperature in degrees Celsius from which a heat notification is shown */
    val weatherAlertHeatTemperature: Int = 35,
    val weatherAlertFrost: Boolean = true,
    /** Temperature in degrees Celsius at or below which a frost notification is shown */
    val weatherAlertFrostTemperature: Int = 0,
    val weatherAlertWind: Boolean = true,
    /** Wind speed in km/h from which a wind notification is shown */
    val weatherAlertWindSpeed: Int = 60,
    val weatherAlertUv: Boolean = false,
    val weatherAlertUvIndex: Int = 8,
    /** How many hours ahead the forecast is checked */
    val weatherAlertHours: Int = 24,
    /** When each kind of alert was last shown (millis), so that it is not repeated for 12 hours */
    val weatherAlertLastSent: Map<String, Long> = emptyMap(),
)

@Serializable
data class LocationSearchGroup(
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
)

@Serializable
data class SearchFilterGroup(
    val searchFilter: SearchFilters = SearchFilters(),
    val searchFilterBar: Boolean = true,
    val searchFilterBarItems: List<KeyboardFilterBarItem> = listOf(
        KeyboardFilterBarItem.Apps,
        KeyboardFilterBarItem.Files,
        KeyboardFilterBarItem.Contacts,
        KeyboardFilterBarItem.OnlineResults,
        KeyboardFilterBarItem.Shortcuts,
        KeyboardFilterBarItem.Events,
        KeyboardFilterBarItem.Reminders,
        KeyboardFilterBarItem.Documents,
        KeyboardFilterBarItem.Images,
        KeyboardFilterBarItem.Video,
        KeyboardFilterBarItem.Music,
        KeyboardFilterBarItem.Articles,
        KeyboardFilterBarItem.Websites,
        KeyboardFilterBarItem.Places,
        KeyboardFilterBarItem.Tools,
        KeyboardFilterBarItem.HiddenResults,
    ),
)

@Serializable
data class LocaleGroup(
    @JsonNames("clockWidgetTimeFormat")
    val localeTimeFormat: TimeFormat = TimeFormat.System,
    val localeMeasurementSystem: MeasurementSystem = MeasurementSystem.System,
    /**
     * The ID of the transliterator to use. The empty string means to pick a transliterator
     * automatically. null disables the transliterator.
     */
    val localeTransliterator: String? = "",
    /**
     * The ICU id of the primary calendar. `null` to use the default.
     */
    val localePrimaryCalendar: String? = null,
    /**
     * The ICU id of the secondary calendar. `null` to disable.
     */
    val localeSecondaryCalendar: String? = null,
    /**
     * Preferred currencies. These currencies are listed first in the currency converters.
     * ISO 4217 codes, e.g. "USD" for US Dollar, "EUR" for Euro, etc.
     * If empty, the default order is determined by the system locale.
     */
    val localeCurrencies: List<String> = emptyList(),
)

@Serializable
data class FeedGroup(
    val feedProviderPackage: String? = null,
)

@Serializable
data class FreezeGroup(
    val freezeBackend: FreezeBackendPreference = FreezeBackendPreference.Auto,
    val freezeAutoFreezeEnabled: Boolean = false,
    val freezeOnRecentsRemoval: Boolean = false,
    val freezeRecentsThawed: Set<String> = emptySet(),
    val freezeOnScreenOff: Boolean = false,
    val freezeOnIdle: Boolean = false,
    val freezeIdleTimeoutMinutes: Int = 15,
    val freezeOnBatterySaver: Boolean = false,
    val freezeCandidates: Set<String> = emptySet(),
    val freezeProfile: FreezeProfile = FreezeProfile.Balanced,
    val freezeExclusionStrictness: FreezeExclusionStrictness = FreezeExclusionStrictness.Strict,
    val freezeNeverFreezeApps: Set<String> = emptySet(),
    val freezeStats: Map<String, FreezeAppStats> = emptyMap(),
    val freezeExcludeForeground: Boolean = true,
    val freezeExcludeMusic: Boolean = true,
    val freezeExcludeNetwork: Boolean = true,
    val freezeNetworkThresholdKb: Int = 100,
    val freezeMethods: Map<String, FreezeMethod> = emptyMap(),
    /** Device-local restoration journal, excluded from portable backups. */
    val freezeMaintenance: Map<String, FrozenAppRestore> = emptyMap(),
    val freezeAdvancedFeaturesEnabled: Boolean = false,
    /** Hides currently-frozen apps from the home screen, app drawer and search entirely - they
     * remain fully manageable (freeze/unfreeze) from the Freeze Manager itself. Default off:
     * frozen apps stay visible (grayed out), matching prior behavior. */
    val freezeHideFromLauncher: Boolean = false,
    // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
    val frozenAppStyle: FrozenAppStyle = FrozenAppStyle.SnowflakeBadge,
    // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===
    /** Epoch millis until auto-freeze is paused after a Play Store update session. 0 = not paused. */
    val playUpdatePauseUntil: Long = 0L,
    /** Packages thawed for a Play Store update; refrozen when the pause ends if still freeze candidates. */
    val playUpdateThawed: Set<String> = emptySet(),
    /** versionCode at thaw time; a higher installed version means Play finished the update. */
    val playUpdateBaselines: Map<String, Long> = emptyMap(),
    /** Packages whose firewall blockAll we set for a Play update; value is whether blockAll was already on. */
    val playUpdateNetBlocked: Map<String, Boolean> = emptyMap(),
    val playUpdatePlayStoreHeld: Boolean = false,
    /** Check upstream descriptions on each manager entry, preserving an explicit opt-out. */
    val freezeBloatAutoUpdate: Boolean = true,
    val freezeSystemRiskApproved: Set<String> = emptySet(),
    /** Missing entry inherits profile; zero freezes immediately; positive values are minutes. */
    val freezeScreenOffDelays: Map<String, Int> = emptyMap(),
    /** Pending alarm deadlines use elapsed realtime, and never survive a device reboot. */
    val freezeScreenOffPending: Map<String, Long> = emptyMap(),
    val freezeScreenOffBoot: Int = -1,
    /** Explicit opt-in, including on upgrades from the old inferred companion prompt. */
    val freezeCompanionEnabled: Set<String> = emptySet(),
    val freezeCompanionExtras: Map<String, String> = emptyMap(),
    val freezeCompanionHolds: Map<String, String> = emptyMap(),
    val freezeGameBoosterPackage: String = "",
    val freezeGameBoosterApps: Set<String> = emptySet(),
)

@Serializable
data class ProtectionGroup(
    val protectionLockSensitiveSettings: Boolean = false,
    val protectionLockMethod: SettingsLockMethod = SettingsLockMethod.DeviceCredential,
    val protectionUseCustomLock: Boolean = false,
    val protectionCustomLockHashed: String? = null,
    val protectionLockLauncher: Boolean = false,
)

@Serializable
data class AppLockGroup(
    val appLockEnabled: Boolean = false,
    val appLockMethod: SettingsLockMethod = SettingsLockMethod.DeviceCredential,
    val appLockDetectionMode: AppLockDetectionMode = AppLockDetectionMode.Hybrid,
    val appLockLockedPackages: Set<String> = emptySet(),
    /** Default grace period (ms): how long after leaving a locked app it can be returned to
     * without re-authenticating. 0 = immediately re-lock. Per-app overrides in [appLockGracePeriodOverrides]. */
    val appLockDefaultGracePeriodMs: Long = 0L,
    val appLockGracePeriodOverrides: Map<String, Long> = emptyMap(),
    /** Require authentication (using [appLockMethod]) to pause or resume the work profile from
     * the app drawer's work tab. Independent of [appLockEnabled] - protects a different surface
     * (a system action, not launching a package). */
    val appLockLockWorkProfileToggle: Boolean = false,
    /** Web app shortcut keys (see WebAppShortcut.key) gated behind authentication before they
     * open - independent of [appLockLockedPackages], which only matches installed packages. */
    val appLockLockedWebAppShortcuts: Set<String> = emptySet(),
    /** Opt-in: silently take a front-camera photo on a failed App Lock authentication attempt.
     * Off by default. Photos are stored in app-private internal storage only. */
    val appLockIntruderPhotoEnabled: Boolean = false,
    /** How long a captured intruder photo is kept before auto-deletion. Clamped to at most
     * [de.mm20.launcher2.preferences.applock.INTRUDER_PHOTO_MAX_RETENTION_DAYS] wherever it's
     * set, not just here, so no stored value can ever exceed that cap. */
    val appLockIntruderPhotoRetentionDays: Int = 90,
    /** A persisted SAF tree uri (ACTION_OPEN_DOCUMENT_TREE) to store intruder photos in, instead
     * of the default app-private internal storage. Null = use the default location. */
    val appLockIntruderPhotoStorageUri: String? = null,
    /** Whether intruder photos should be discoverable by the system gallery/Photos app. Only
     * meaningful when [appLockIntruderPhotoStorageUri] is set to a folder outside app-private
     * storage, since internal storage is never gallery-visible regardless of this flag. Off by
     * default - a captured intruder photo should not surface anywhere the intruder (or anyone
     * else with the device) might casually stumble onto it. */
    val appLockIntruderPhotoVisibleInGallery: Boolean = false,
    /** Opt-in, independent of [appLockIntruderPhotoEnabled]: post a notification on a failed App
     * Lock attempt (regardless of whether a photo was actually captured), with a "show more"
     * action that opens the captured-photos gallery. Off by default. */
    val appLockIntruderPhotoNotificationEnabled: Boolean = false,
    val appLockRelockOnlyOnScreenOff: Boolean = false,
)

@Serializable
data class FloatingLauncherGroup(
    val floatingLauncherEnabled: Boolean = false,
    @Deprecated("Replaced by floatingLauncherZones - kept only so Migration10 can read the old single-tab position.")
    val floatingLauncherEdge: FloatingLauncherEdge = FloatingLauncherEdge.Right,
    @Deprecated("Replaced by floatingLauncherZones - kept only so Migration10 can read the old single-tab position.")
    val floatingLauncherPosition: Float = 0.5f,
    /** One tab per enabled zone; each zone has its own app list. Only RightTop is enabled by
     * default - the other five are opt-in. */
    val floatingLauncherZones: Map<FloatingLauncherZone, FloatingLauncherZoneConfig> = mapOf(
        FloatingLauncherZone.RightTop to FloatingLauncherZoneConfig(enabled = true),
    ),
    /** Shared across every zone - simpler than a per-zone setting, and there's no real use case
     * for zones wanting a different column count from each other. */
    val floatingLauncherColumns: Int = 2,
    /** Caps how many rows the panel shows before it scrolls, per column. */
    val floatingLauncherMaxPerColumn: Int = 10,
    val floatingLauncherThickness: Int = 24,
    @Serializable(with = ColorIntAsHexSerializer::class)
    val floatingLauncherColor: Int = 0xFF9E9E9E.toInt(),
    val floatingLauncherAlpha: Float = 0.8f,
    val floatingLauncherHideIndicator: Boolean = false,
    val floatingLauncherHapticFeedback: Boolean = true,
    val floatingLauncherAutoHideGaming: Boolean = false,
    /** Show the app names under the icons (the Smart Sidebar's "Hide labels" turned around) */
    val floatingLauncherShowLabels: Boolean = true,
    /** Opacity of the panel card, [0.3f, 1f] */
    val floatingLauncherPanelAlpha: Float = 0.85f,
    /** Size of the icons in the panel, in dp */
    val floatingLauncherIconSize: Int = 48,
    /** Open tapped apps in a floating window (needs freeform windows, see Desktop mode) */
    val floatingLauncherFloatingWindows: Boolean = true,
    /** Not used any more, the tools are items of the sidebar now. Kept so that stored settings stay readable. */
    val floatingLauncherTools: Boolean = true,
    /**
     * What the sidebar shows, in order: app keys, `tool:<id>` for tools and `widget:<uuid>` for
     * widgets. Replaces the per-zone app lists.
     */
    val floatingLauncherItems: List<String> = listOf("tool:screenshot", "tool:recent_files", "tool:flashlight"),
    /** The edge the handle is on */
    val floatingLauncherSide: FloatingLauncherEdge = FloatingLauncherEdge.Right,
    /** Vertical position of the handle, 0 is the top and 1 the bottom */
    val floatingLauncherHandleY: Float = 0.5f,
    /** Height of the handle in dp */
    val floatingLauncherHandleHeight: Int = 72,
    /** The File Dock (temporary storage for dragged items) */
    val floatingLauncherFileDock: Boolean = true,
)

@Serializable
data class DynamicIslandGroup(
    val dynamicIslandEnabled: Boolean = false,
    /** Optional - the pill only shows a call while active if this is granted. */
    val dynamicIslandShowCalls: Boolean = true,
)

@Serializable
data class WebAppsPanelGroup(
    /**
     * Keys of the WebAppShortcuts shown in the Web Apps Panel, in order. Membership here is
     * independent of whether a shortcut also appears in search results - a shortcut can exist
     * without being pinned to the panel. The panel itself is reached via whichever gesture slot
     * has [GestureAction.WebAppsPanel] assigned - there's no separate enabled/direction field.
     */
    val webAppsPanelItems: List<String> = emptyList(),
)

@Serializable
data class WebAppBrowsingGroup(
    /** Preferred embedded engine for web app shortcuts. */
    val webAppEngine: String = "webview",
    /** Applies to the embedded WebView renderer used by web app shortcuts (not Custom Tabs). */
    val webAppAdBlockEnabled: Boolean = true,
    val webAppTrackingParamStrippingEnabled: Boolean = true,
    val webAppZoomControlsEnabled: Boolean = true,
    /** Whether the web app renderer's top bar (nav arrows, menu) is pinned to the bottom of the
     * screen instead of the top. */
    val webAppTopBarAtBottom: Boolean = false,
    val webAppSwipeToSwitchEnabled: Boolean = true,
    val webAppGroupsEnabled: Boolean = false,
    val webAppGroups: List<WebAppGroup> = emptyList(),
    /** One of "default", "desktop", "custom". Unknown values are treated as "default". */
    val webAppUserAgentMode: String = "default",
    val webAppCustomUserAgent: String = "",
    /** Whether the embedded renderer accepts cookies at all. */
    val webAppCookiesEnabled: Boolean = true,
    val webAppThirdPartyCookiesEnabled: Boolean = false,
)

@Serializable
data class ContextProfilesGroup(
    val contextProfilesEnabled: Boolean = false,
    val contextProfiles: List<ContextProfile> = emptyList(),
    /** If set, this profile is force-active regardless of trigger evaluation. */
    val contextProfileManualOverrideId: String? = null,
)

@Serializable
data class DesktopModeGroup(
    val desktopModeEnabled: Boolean = false,
    /** True once the user has explicitly touched the enable toggle (either direction), or
     * auto-enable has already fired once - prevents auto-enable from firing repeatedly or
     * fighting a deliberate opt-out. */
    val desktopModeUserConfigured: Boolean = false,
    val desktopModeOrientation: DesktopModeOrientation = DesktopModeOrientation.Auto,
    /**
     * User's intent for freeform windowing, separate from the OS-level `enable_freeform_support`
     * setting this drives - the two can drift apart (e.g. another app changed the system setting)
     * so this is only ever the last state the user explicitly chose via the toggle.
     */
    val desktopModeFreeformEnabled: Boolean = false,
    /** Separate from the phone's wallpaper - the phone's video wallpaper can't render on the
     * external display, and a phone-shaped video wouldn't fit a landscape monitor anyway. */
    val desktopWallpaperMode: DesktopWallpaperMode = DesktopWallpaperMode.SolidColor,
    val desktopWallpaperImageUri: String? = null,
    /** Independent of the phone's own gridIconSize - external displays are bigger and viewed
     * from farther away, so the same icon size wouldn't make sense on both. */
    val desktopGridIconSize: Int = 48,
    /** Super+Space search, Super+Enter Terminal, Super+1 home. Also used on the phone with a hardware keyboard. */
    val hardwareKeyboardShortcuts: Boolean = true,
)

@Serializable
data class AgentsGroup(
    /** Coding / chat agents in search. Off until the user turns them on. */
    val agentsEnabled: Boolean = false,
    val agentId: String = "grok",
    /** auto: installed app, then website. web, app, or termux. */
    val agentTransport: String = "auto",
    /** If true, Enter sends a sentence (3+ words) to the agent instead of launching the top app. */
    val agentsSendSentences: Boolean = true,
    val searchBarVoice: Boolean = true,
)

@Serializable
data class BatteryGroup(
    val batteryGuardEnabled: Boolean = false,
    val batteryScreenOffGps: Boolean = true,
    val batteryScreenOffData: Boolean = false,
    val batteryScreenOffMic: Boolean = false,
    val batteryScreenOffCamera: Boolean = false,
    val batteryScreenOffSync: Boolean = false,
    /** empty = leave radio; lte, wcdma, gsm */
    val batteryScreenOffRadio: String = "",
    val batteryWeatherWakeMinutes: Int = 60,
    val batterySyncWakeMinutes: Int = 0,
    val batteryGpsHoldPackages: String = "",
    val batteryCallHoldPackages: String = "",
)

@Serializable
data class TweaksGroup(
    /** Re-run saved tweaks after reboot (needs root or Shizuku). */
    val persistOnBoot: Boolean = false,
    val clockSeconds: Boolean = false,
    val peakRefresh120: Boolean = false,
    val disableSafeVolume: Boolean = false,
    val callVibrateConnect: Boolean = false,
    val mobileDataAlwaysOn: Boolean = false,
    val alwaysOnDisplay: Boolean = false,
)

@Serializable
enum class ColorScheme {
    Light,
    Dark,
    System,
    /** Switches between light and dark automatically based on the time of day. */
    Time,
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
    data class Widgets(val target: WidgetScreenTarget = WidgetScreenTarget.Default) : GestureAction

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

    /** Opens one of the built-in Telos apps (Phone, Messages, Notes, Calendar, ...) with the virtual app [key] */
    @Serializable
    @SerialName("telos_app")
    data class TelosApp(val key: String = "") : GestureAction

    /**
     * Shows one of the built-in Telos apps as a page inside the launcher (reached with the gesture)
     * instead of starting its own screen. [key] is the virtual app key, same as [TelosApp].
     */
    @Serializable
    @SerialName("telos_page")
    data class TelosPage(val key: String = "") : GestureAction

    @Serializable
    @SerialName("launcher_settings")
    data object LauncherSettings : GestureAction

    /** Invokes the action with id [actionId] on the GestureAction plugin at [authority]. */
    @Serializable
    @SerialName("plugin_action")
    data class Plugin(val authority: String, val actionId: String) : GestureAction

    /**
     * Opens the Web Apps Panel (a dedicated grid of web app shortcuts, distinct from the
     * Floating Launcher sidebar). Assigning this to a gesture slot (typically swipeLeft or
     * swipeRight) is how the panel's direction is chosen - there is no separate
     * enabled/direction preference, this assignment IS the on/off + direction state.
     */
    @Serializable
    @SerialName("web_apps_panel")
    data object WebAppsPanel : GestureAction

    /** Shows a small menu (change wallpaper / add widget) - the same thing a long-press on an
     * empty area of the home screen shows on most stock launchers. */
    @Serializable
    @SerialName("home_screen_menu")
    data object HomeScreenMenu : GestureAction
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
    @SerialName("documents") Documents,
    @SerialName("images") Images,
    @SerialName("video") Video,
    @SerialName("music") Music,
    @SerialName("shortcuts") Shortcuts,
    @SerialName("contacts") Contacts,
    @SerialName("events") Events,
    @SerialName("reminders") Reminders,
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

@Serializable
enum class BatteryStatusVisibility {
    @SerialName("hide") Hide,
    @SerialName("show") Show,
    @SerialName("always") Always
}

@Serializable
enum class FreezeBackendPreference {
    @SerialName("auto") Auto,
    @SerialName("shizuku") ShizukuOnly,
    // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
    @SerialName("dhizuku") DhizukuOnly,
    // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
    @SerialName("root") RootOnly,
    @SerialName("island") Island,
    @SerialName("device_owner") DeviceOwnerOnly,
}

/**
 * How protected settings screens are unlocked. Both modes go through the system's
 * BiometricPrompt - the launcher never stores or verifies credentials itself.
 */
@Serializable
enum class SettingsLockMethod {
    /** System biometrics, falling back to the device PIN/pattern/password. */
    @SerialName("device_credential") DeviceCredential,

    /** Biometrics only: the device PIN/pattern is deliberately not accepted. */
    @SerialName("biometrics_only") BiometricsOnly,
}

/**
 * How App Lock notices that a locked app just came to the foreground. Both paths end up calling
 * the same gate; this only controls detection latency/permission trade-offs.
 */
@Serializable
enum class AppLockDetectionMode {
    /** UsageStatsManager polling only (needs Usage Access, granted via Settings). */
    @SerialName("usage_stats") UsageStats,

    /** Real-time WINDOW_STATE_CHANGED events from the launcher's accessibility service only. */
    @SerialName("accessibility") Accessibility,

    /** Both at once: instant via accessibility when it's enabled, polling as a fallback
     * whenever it isn't (or hasn't connected yet). */
    @SerialName("hybrid") Hybrid,
}

/** Which screen edge the floating quick launcher's collapsed tab attaches to. */
@Serializable
enum class FloatingLauncherEdge {
    @SerialName("left") Left,
    @SerialName("right") Right,
}

/** Six independently-toggleable trigger zones - each screen edge split into thirds. */
@Serializable
enum class FloatingLauncherZone {
    @SerialName("left_top") LeftTop,
    @SerialName("left_middle") LeftMiddle,
    @SerialName("left_bottom") LeftBottom,
    @SerialName("right_top") RightTop,
    @SerialName("right_middle") RightMiddle,
    @SerialName("right_bottom") RightBottom;

    val isLeftEdge: Boolean
        get() = this == LeftTop || this == LeftMiddle || this == LeftBottom

    /** Vertical anchor within the usable screen height, as a 0..1 fraction (thirds, centered). */
    val verticalFraction: Float
        get() = when (this) {
            LeftTop, RightTop -> 1f / 6f
            LeftMiddle, RightMiddle -> 0.5f
            LeftBottom, RightBottom -> 5f / 6f
        }
}

@Serializable
sealed interface SidebarPanelConfig {
    @Serializable
    @SerialName("apps")
    data class AppGrid(
        val apps: List<String> = emptyList(),
        val folders: List<FloatingLauncherFolder> = emptyList(),
    ) : SidebarPanelConfig

    @Serializable
    @SerialName("plugin")
    data class Plugin(
        val authority: String,
        val panelId: String,
    ) : SidebarPanelConfig
}

@Serializable
data class FloatingLauncherZoneConfig(
    val enabled: Boolean = false,
    val panels: List<SidebarPanelConfig> = emptyList(),
    val activePanelIndex: Int = 0,
    /** Deprecated: migrated to [panels] */
    val apps: List<String> = emptyList(),
    /** Deprecated: migrated to [panels] */
    val folders: List<FloatingLauncherFolder> = emptyList(),
)

@Serializable
data class FloatingLauncherFolder(
    val id: String,
    val name: String,
    /** SavableSearchable keys inside this folder, in display order. */
    val appKeys: List<String> = emptyList(),
) {
    companion object {
        private const val KEY_PREFIX = "floating_folder:"

        fun sentinelKey(id: String) = "$KEY_PREFIX$id"

        fun idFromSentinel(key: String): String? =
            key.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX)
    }
}

/** Requested orientation for the desktop shell activity on the external display. */
@Serializable
enum class DesktopModeOrientation {
    @SerialName("auto") Auto,
    @SerialName("portrait") Portrait,
    @SerialName("landscape") Landscape,
}

enum class DesktopWallpaperMode {
    /** Solid theme-color background - the default, since it needs no picked image. */
    @SerialName("solid_color") SolidColor,
    /** A separate static image, picked independently of the phone's wallpaper. */
    @SerialName("static_image") StaticImage,
}

@Serializable
enum class VideoWallpaperScalingMode {
    /** Letterbox: whole video visible, black bars if aspect ratios differ. */
    @SerialName("fit") Fit,
    /** Crop to cover the whole screen. */
    @SerialName("fill") Fill,
    /** Distort to exactly match the screen. */
    @SerialName("stretch") Stretch,
}

@Serializable
enum class NotificationBadgeStyle {
    /** A plain colored dot, no number. This is the pre-existing look most users already see,
     *  since most apps never set a meaningful Notification.number, so summed counts were
     *  usually 0 anyway. */
    @SerialName("dot") Dot,
    /** The number of active (non-summary) notifications from the app. */
    @SerialName("count") Count,
}

@Serializable
enum class VideoWallpaperStartBehavior {
    @SerialName("resume") Resume,
    @SerialName("restart") Restart,
    @SerialName("random") Random,
}


@Serializable
sealed interface DockItem {
    @Serializable
    @SerialName("searchable")
    data class Searchable(val key: String) : DockItem

    @Serializable
    @SerialName("widget")
    data class Widget(
        val widgetId: Int,
        val providerPackage: String,
        val providerClassName: String
    ) : DockItem
}

@Serializable
enum class FreezeProfile {
    @SerialName("battery_saver") BatterySaver,
    @SerialName("balanced") Balanced,
    @SerialName("aggressive") Aggressive,
    @SerialName("ultra_aggressive") UltraAggressive,
    @SerialName("custom") Custom,
}

@Serializable
enum class FreezeExclusionStrictness {
    @SerialName("strict") Strict,
    @SerialName("relaxed") Relaxed,
}

// === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
@Serializable
enum class FrozenAppStyle {
    @SerialName("grayscale") Grayscale,
    @SerialName("snowflake_badge") SnowflakeBadge,
}
// === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===

@Serializable
enum class FreezeMethod {
    @SerialName("suspend") Suspend,
    @SerialName("disable") Disable,
}

@Serializable
data class FreezeAppStats(
    val freezeCount: Int = 0,
    val unfreezeCount: Int = 0,
    val lastFrozenAt: Long? = null,
    val lastUnfrozenAt: Long? = null,
)

// === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
@Serializable
data class CommsGroup(
    val showRecentsFavorites: Boolean = true,
    val speedDials: Map<Int, String> = emptyMap(),
    val t9Alphabet: String = "latin",
    val defaultSim: String = "ask",
    val dialpadSounds: Boolean = false,
    val dialpadVibration: Boolean = true,
    val vibrateOnAnswer: Boolean = false,
    val vibrateOnHangup: Boolean = false,
    val clirPrefix: String = "",
    val enableSpamBlocking: Boolean = true,
    val systemSpamBlocking: Boolean = true,
    val onlineCallerLookup: Boolean = false,
    val hideDialpadLetters: Boolean = false,
    val callerNotes: Map<String, String> = emptyMap(),
    val numberDefaultSim: Map<String, String> = emptyMap(),
    val blockHiddenNumbers: Boolean = false,
    val blockUnknownNumbers: Boolean = false,
    val blockInternational: Boolean = false,
    /** Also reject outgoing calls to numbers on the block list / prefix / regex rules. */
    val blockOutgoingCalls: Boolean = true,
    /** ISO 3166 region of the home country; empty = SIM, then network, then locale country */
    val homeCountry: String = "",
    val clirEnabled: Boolean = false,
    val autoRecordCalls: Boolean = false,
    val autoRecordMode: String = "ALL_EXCEPT_EXCEPTIONS",
    val recordProhibitedNumbers: Set<String> = emptySet(),
    val recordMandatoryNumbers: Set<String> = emptySet(),
    val recordingQuality: String = "BALANCED",
    /** auto | shizuku | root | unprivileged */
    val recordingBackend: String = "auto",
    val rememberDialpad: Boolean = true,
    val lastDialpadDigits: String = "",
    val confirmBeforeCall: Boolean = false,
    val tapToCall: Boolean = true,
    val autoRedial: Boolean = false,
    val autoRedialAttempts: Int = 3,
    val autoRedialDelaySec: Int = 5,
    val autoOpenDialpad: Boolean = false,
    val rejectSmsTemplate: String = "I'll call you back",
    val lastUsedSim: String = "",
    val pocketMode: Boolean = false,
    /** buttons | swipe */
    val answerStyle: String = "buttons",
    val fullScreenCallerPhoto: Boolean = false,
    val sim1Color: String = "green",
    val sim2Color: String = "blue",
    /** Subscription id → user label for the SIM in Phone and Messages. Empty uses Android's SIM name. */
    val simNames: Map<String, String> = emptyMap(),
    /** Keys of Telos virtual apps (Phone, Music, ...) that were removed in the Store, comma separated */
    val disabledVirtualApps: String = "",
    /** Telos Media: the space that was open last (music, radio or video) */
    val mediaHubSpace: String = "music",
    /** Telos Media: order of the spaces in the pill, comma separated ids (empty = music, radio, video, tv) */
    val mediaHubOrder: String = "",
    /** Compact list (small icons + text) for radio/TV in Telos Media */
    val mediaHubCompactList: Boolean = true,
    /** Live FFT waveform on the Music library; default on. */
    val mediaMusicSpectrum: Boolean = true,
    /** Phone bottom bar SMS dest. When [phoneShowSmsTabUserSet] is false, hide if Telos is not the default SMS app. */
    val phoneShowSmsTab: Boolean = true,
    val phoneShowSmsTabUserSet: Boolean = false,
    /** Telos Video: online services. Keys and passwords are encrypted with the Android Keystore. */
    val tmdbApiKeyEnc: String = "",
    val subtitleApiKeyEnc: String = "",
    val subtitleUser: String = "",
    val subtitlePasswordEnc: String = "",
    /** Comma separated language codes, most wanted first */
    val subtitleLanguages: String = "en",
    val subtitleAutoDownload: Boolean = false,
    /** Torrent streaming only on Wi-Fi (or other unmetered networks) */
    val torrentWifiOnly: Boolean = true,
    /** SIP account switched on: registered in the background so that calls can be received */
    val sipEnabled: Boolean = false,
    val sipUser: String = "",
    /** Registrar, for a FRITZ!Box usually fritz.box */
    val sipDomain: String = "fritz.box",
    val sipDisplayName: String = "",
    /** Verify the TLS certificate of the server. Off for accounts saved before this option existed (self-signed FRITZ!Box). */
    val sipVerifyServer: Boolean = false,
    /** Encrypted with the Android Keystore, never stored in plain text */
    val sipPasswordEnc: String = "",
    /** off = receive only | choose = extra SIP button when calling | default = SIP preferred */
    val sipOutgoing: String = "choose",
    val remotePhonebookEnabled: Boolean = false,
    val remotePhonebookHost: String = "fritz.box",
    val remotePhonebookUser: String = "",
    /** Encrypted with the Android Keystore, never stored in plain text */
    val remotePhonebookPasswordEnc: String = "",
    /** Blocks screenshots and recents thumbnails on the call screen */
    val secureCallScreen: Boolean = false,
    /** 0 = keep forever */
    val recordingAutoDeleteDays: Int = 0,
    val proximitySpeaker: Boolean = false,
    val showNumbersInRecents: Boolean = true,
    val missedCallPopup: Boolean = true,
    val postCallPopup: Boolean = false,
    val inCallNotes: Boolean = true,
    val contactDefaultNumbers: Map<String, String> = emptyMap(),
    /** Phone number digits → messenger id (phone|signal|whatsapp|telegram|viber) for outgoing calls */
    val contactDefaultCallApp: Map<String, String> = emptyMap(),
    /** Phone number digits → messenger id (sms|signal|whatsapp|telegram|viber) for outgoing messages */
    val contactDefaultMessageApp: Map<String, String> = emptyMap(),
    val archivedConversations: Map<String, String> = emptyMap(),
    val pinnedConversations: Map<String, String> = emptyMap(),
    val conversationNames: Map<String, String> = emptyMap(),
    val smsSignature: String = "",
    val unreadOnTop: Boolean = true,
    val smsAutoDeleteDays: Int = 0,
    val blockAlphanumeric: Boolean = false,
    val smsPopupReply: Boolean = false,
    val smsE2eEnabled: Boolean = false,
    val smsBlockRegex: String = "",
    val hiddenNumbers: Map<String, String> = emptyMap(),
    val hideFromContacts: Boolean = true,
    val hideFromRecents: Boolean = true,
    val maskHiddenIncoming: Boolean = true,
    val stealthHiderMenu: Boolean = false,
    val hiddenNotifDisguised: Boolean = true,
    val hiddenNotifTitle: String = "System Update",
    val hiddenNotifText: String = "Checking for system updates...",
    val hiddenMuteRingtone: Boolean = true,
    val hiddenHideCallScreen: Boolean = true,
    val dndFavoritesOnlyCall: Boolean = true,
    val phoneAppLock: Boolean = false,
    /** Require system or launcher lock to open call recordings. On by default. */
    val lockCallRecordings: Boolean = true,
    /** Write `.nomedia` in CallRecordings. Off by default. */
    val hideCallRecordingsFromMedia: Boolean = false,
    val callProtectMode: String = "none",
    val protectedCallNumbers: Map<String, String> = emptyMap(),
    val raiseToAnswer: Boolean = false,
    val flipToDecline: Boolean = false,
    val rainMode: Boolean = false,
    val volumeDnd: Boolean = false,
    val volumeDndLockOnly: Boolean = true,
    val preferredNetworkMode: String = "auto",
    val networkBackend: String = "auto",
    val screenOffLte: Boolean = false,
    val batterySaverLte: Boolean = false,
    /** Phones: the dialer stays in portrait unless this is on. Tablets always rotate. */
    val allowDialerLandscape: Boolean = false,
    /** default | vulkan | opengl — system HWUI renderer (debug.hwui.renderer) */
    val hwuiRenderer: String = "default",
    val hwuiRestoreOnBoot: Boolean = true,
    val hwuiShowRestartNotice: Boolean = true,
    /** Allow Vulkan/OpenGL tap-switch on the System home widget. */
    val hwuiWidgetSwitch: Boolean = false,
)
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===

@Serializable
data class FrozenAppRestore(
    val packageName: String,
    val userId: Int,
    val suspended: Boolean,
    val enabledState: Int,
    val hidden: Boolean,
    val components: Map<String, Int> = emptyMap(),
    val installer: Boolean = false,
    val backend: String = "",
    val operationId: String = "",
)
