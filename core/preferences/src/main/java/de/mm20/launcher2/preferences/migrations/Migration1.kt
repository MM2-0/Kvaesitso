package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.BaseLayout
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.preferences.Font
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.LauncherSettingsData
import de.mm20.launcher2.preferences.LegacyDataStore
import de.mm20.launcher2.preferences.LegacySettings
import de.mm20.launcher2.preferences.ScreenOrientation
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.SearchResultOrder
import de.mm20.launcher2.preferences.SurfaceShape
import de.mm20.launcher2.preferences.SystemBarColors
import de.mm20.launcher2.preferences.ThemeDescriptor
import de.mm20.launcher2.preferences.WeightFactor
import kotlinx.coroutines.flow.first
import java.util.UUID

class Migration1(
    private val legacyDataStore: LegacyDataStore,
) : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {

    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return legacyDataStore.data.first().version > 0 && currentData.schemaVersion < 1
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        val legacyData = legacyDataStore.data.first()
        return currentData.copy(
            schemaVersion = 1,
            uiBaseLayout = when (legacyData.layout.baseLayout) {
                LegacySettings.LayoutSettings.Layout.Pager -> BaseLayout.PullDown
                LegacySettings.LayoutSettings.Layout.PagerReversed -> BaseLayout.PagerReversed
                else -> BaseLayout.PullDown
            },
            gridColumnCount = legacyData.grid.columnCount,
            animationsCharging = legacyData.animations.charging,
            badgesCloudFiles = legacyData.badges.cloudFiles,
            badgesNotifications = legacyData.badges.notifications,
            badgesShortcuts = legacyData.badges.shortcuts,
            badgesSuspendedApps = legacyData.badges.suspendedApps,
            calculatorEnabled = legacyData.calculatorSearch.enabled,
            calendarSearchEnabled = legacyData.calendarSearch.enabled,
            clockWidgetAlarmPart = legacyData.clockWidget.alarmPart,
            clockWidgetBatteryPart = legacyData.clockWidget.batteryPart,
            clockWidgetDatePart = legacyData.clockWidget.datePart,
            clockWidgetColors = when (legacyData.clockWidget.color) {
                LegacySettings.ClockWidgetSettings.ClockWidgetColors.Light -> ClockWidgetColors.Light
                LegacySettings.ClockWidgetSettings.ClockWidgetColors.Dark -> ClockWidgetColors.Dark
                else -> ClockWidgetColors.Auto
            },
            clockWidgetAlignment = when (legacyData.clockWidget.alignment) {
                LegacySettings.ClockWidgetSettings.ClockWidgetAlignment.Top -> ClockWidgetAlignment.Top
                LegacySettings.ClockWidgetSettings.ClockWidgetAlignment.Center -> ClockWidgetAlignment.Center
                else -> ClockWidgetAlignment.Bottom
            },
            homeScreenDock = legacyData.clockWidget.favoritesPart,
            clockWidgetFillHeight = legacyData.clockWidget.fillHeight,
            clockWidgetCompact = legacyData.clockWidget.layout == LegacySettings.ClockWidgetSettings.ClockWidgetLayout.Horizontal,
            clockWidgetMusicPart = legacyData.clockWidget.musicPart,
            clockWidgetStyle = when (legacyData.clockWidget.clockStyle) {
                LegacySettings.ClockWidgetSettings.ClockStyle.DigitalClock1 -> ClockWidgetStyle.Digital1()
                LegacySettings.ClockWidgetSettings.ClockStyle.DigitalClock2 -> ClockWidgetStyle.Digital2
                LegacySettings.ClockWidgetSettings.ClockStyle.OrbitClock -> ClockWidgetStyle.Orbit
                LegacySettings.ClockWidgetSettings.ClockStyle.BinaryClock -> ClockWidgetStyle.Binary
                LegacySettings.ClockWidgetSettings.ClockStyle.AnalogClock -> ClockWidgetStyle.Analog
                LegacySettings.ClockWidgetSettings.ClockStyle.EmptyClock -> ClockWidgetStyle.Empty
                LegacySettings.ClockWidgetSettings.ClockStyle.DigitalClock1_MDY -> ClockWidgetStyle.Digital1(
                    variant = ClockWidgetStyle.Digital1.Variant.MDY
                )

                LegacySettings.ClockWidgetSettings.ClockStyle.DigitalClock1_Outlined -> ClockWidgetStyle.Digital1(
                    outlined = true
                )

                LegacySettings.ClockWidgetSettings.ClockStyle.DigitalClock1_OnePlus -> ClockWidgetStyle.Digital1(
                    variant = ClockWidgetStyle.Digital1.Variant.OnePlus
                )

                else -> ClockWidgetStyle.Digital1()
            },
            contactSearchEnabled = legacyData.contactsSearch.enabled,
            easterEgg = legacyData.easterEgg,
            favoritesEditButton = legacyData.favorites.editButton,
            favoritesEnabled = legacyData.favorites.enabled,
            favoritesFrequentlyUsed = legacyData.favorites.frequentlyUsed,
            favoritesFrequentlyUsedRows = legacyData.favorites.frequentlyUsedRows,
            rankingWeightFactor = when (legacyData.resultOrdering.weightFactor) {
                LegacySettings.SearchResultOrderingSettings.WeightFactor.Low -> WeightFactor.Low
                LegacySettings.SearchResultOrderingSettings.WeightFactor.High -> WeightFactor.High
                else -> WeightFactor.Default
            },
            fileSearchProviders = buildSet {
                if (legacyData.fileSearch.localFiles) add("local")
                if (legacyData.fileSearch.nextcloud) add("nextcloud")
                if (legacyData.fileSearch.gdrive) add("gdrive")
                if (legacyData.fileSearch.onedrive) add("onedrive")
            },
            gesturesDoubleTap = makeGestureSettings(
                legacyData.gestures.doubleTap,
                legacyData.gestures.doubleTapApp
            ),
            gesturesSwipeDown = makeGestureSettings(
                legacyData.gestures.swipeDown,
                legacyData.gestures.swipeDownApp
            ),
            gesturesSwipeLeft = makeGestureSettings(
                legacyData.gestures.swipeLeft,
                legacyData.gestures.swipeLeftApp
            ),
            gesturesSwipeRight = makeGestureSettings(
                legacyData.gestures.swipeRight,
                legacyData.gestures.swipeRightApp
            ),
            gesturesLongPress = makeGestureSettings(
                legacyData.gestures.longPress,
                legacyData.gestures.longPressApp
            ),
            gesturesHomeButton = makeGestureSettings(
                legacyData.gestures.homeButton,
                legacyData.gestures.homeButtonApp
            ),
            gridIconSize = legacyData.grid.iconSize,
            gridLabels = legacyData.grid.showLabels,
            mediaAllowList = legacyData.musicWidget.allowListList.toSet(),
            mediaDenyList = legacyData.musicWidget.denyListList.toSet(),
            hiddenItemsShowButton = legacyData.searchBar.hiddenItemsButton,
            iconsAdaptify = legacyData.icons.adaptify,
            iconsForceThemed = legacyData.icons.forceThemed,
            iconsPack = legacyData.icons.iconPack.takeIf { it.isNotBlank() },
            iconsPackThemed = legacyData.icons.iconPackThemed,
            iconsShape = when (legacyData.icons.shape) {
                LegacySettings.IconSettings.IconShape.Circle -> IconShape.Circle
                LegacySettings.IconSettings.IconShape.Square -> IconShape.Square
                LegacySettings.IconSettings.IconShape.Squircle -> IconShape.Squircle
                LegacySettings.IconSettings.IconShape.RoundedSquare -> IconShape.RoundedSquare
                LegacySettings.IconSettings.IconShape.EasterEgg -> IconShape.EasterEgg
                LegacySettings.IconSettings.IconShape.Hexagon -> IconShape.Hexagon
                LegacySettings.IconSettings.IconShape.Triangle -> IconShape.Triangle
                LegacySettings.IconSettings.IconShape.Pentagon -> IconShape.Pentagon
                LegacySettings.IconSettings.IconShape.Teardrop -> IconShape.Teardrop
                LegacySettings.IconSettings.IconShape.Pebble -> IconShape.Pebble
                else -> IconShape.PlatformDefault
            },
            iconsThemed = legacyData.icons.themedIcons,
            searchBarBottom = legacyData.layout.bottomSearchBar,
            searchBarColors = when (legacyData.systemBars.statusBarColor) {
                LegacySettings.SystemBarsSettings.SystemBarColors.Light -> SearchBarColors.Light
                LegacySettings.SystemBarsSettings.SystemBarColors.Dark -> SearchBarColors.Dark
                else -> SearchBarColors.Auto
            },
            searchBarFixed = legacyData.layout.bottomSearchBar,
            searchBarKeyboard = legacyData.searchBar.autoFocus,
            searchBarStyle = when (legacyData.searchBar.searchBarStyle) {
                LegacySettings.SearchBarSettings.SearchBarStyle.Hidden -> SearchBarStyle.Hidden
                LegacySettings.SearchBarSettings.SearchBarStyle.Solid -> SearchBarStyle.Solid
                else -> SearchBarStyle.Transparent
            },
            searchLaunchOnEnter = legacyData.searchBar.launchOnEnter,
            searchResultOrder = when (legacyData.resultOrdering.ordering) {
                LegacySettings.SearchResultOrderingSettings.Ordering.Alphabetic -> SearchResultOrder.Alphabetical
                LegacySettings.SearchResultOrderingSettings.Ordering.LaunchCount -> SearchResultOrder.LaunchCount
                else -> SearchResultOrder.Weighted
            },
            searchResultsReversed = legacyData.layout.reverseSearchResults,
            shortcutSearchEnabled = legacyData.appShortcutSearch.enabled,
            stateTagsMultiline = legacyData.ui.searchTagsMultiline,
            surfacesBorderWidth = legacyData.cards.borderWidth,
            surfacesOpacity = legacyData.cards.opacity,
            surfacesRadius = legacyData.cards.radius,
            surfacesShape = when (legacyData.cards.shape) {
                LegacySettings.CardSettings.Shape.Cut -> SurfaceShape.Cut
                else -> SurfaceShape.Rounded
            },
            systemBarsHideNav = legacyData.systemBars.hideNavBar,
            systemBarsHideStatus = legacyData.systemBars.hideStatusBar,
            systemBarsNavColors = when (legacyData.systemBars.navBarColor) {
                LegacySettings.SystemBarsSettings.SystemBarColors.Light -> SystemBarColors.Light
                LegacySettings.SystemBarsSettings.SystemBarColors.Dark -> SystemBarColors.Dark
                else -> SystemBarColors.Auto
            },
            systemBarsStatusColors = when (legacyData.systemBars.statusBarColor) {
                LegacySettings.SystemBarsSettings.SystemBarColors.Light -> SystemBarColors.Light
                LegacySettings.SystemBarsSettings.SystemBarColors.Dark -> SystemBarColors.Dark
                else -> SystemBarColors.Auto
            },
            uiColorScheme = when (legacyData.appearance.theme) {
                LegacySettings.AppearanceSettings.Theme.Light -> ColorScheme.Light
                LegacySettings.AppearanceSettings.Theme.Dark -> ColorScheme.Dark
                else -> ColorScheme.System
            },
            uiFont = when (legacyData.appearance.font) {
                LegacySettings.AppearanceSettings.Font.SystemDefault -> Font.System
                else -> Font.Outfit
            },
            uiOrientation = when (legacyData.layout.fixedRotation) {
                true -> ScreenOrientation.Portrait
                else -> ScreenOrientation.Auto
            },
            uiTheme = when (legacyData.appearance.themeId) {
                UUID(0L, 0L).toString() -> ThemeDescriptor.Default
                UUID(0L, 1L).toString() -> ThemeDescriptor.BlackAndWhite
                else -> ThemeDescriptor.Custom(legacyData.appearance.themeId)
            },
            unitConverterCurrencies = legacyData.unitConverterSearch.currencies,
            unitConverterEnabled = legacyData.unitConverterSearch.enabled,
            wallpaperBlur = legacyData.appearance.blurWallpaper,
            weatherImperialUnits = legacyData.weather.imperialUnits,
            wallpaperBlurRadius = legacyData.appearance.blurWallpaperRadius,
            wallpaperDim = legacyData.appearance.dimWallpaper,
            weatherProvider = when (legacyData.weather.provider) {
                LegacySettings.WeatherSettings.WeatherProvider.MetNo -> "metno"
                LegacySettings.WeatherSettings.WeatherProvider.OpenWeatherMap -> "owm"
                LegacySettings.WeatherSettings.WeatherProvider.Here -> "here"
                LegacySettings.WeatherSettings.WeatherProvider.BrightSky -> "dwd"
                else -> "metno"
            },
            websiteSearchEnabled = legacyData.websiteSearch.enabled,
            widgetsEditButton = legacyData.widgets.editButton,
            wikipediaCustomUrl = legacyData.wikipediaSearch.customUrl.takeIf { it.isNotBlank() },
            wikipediaSearchEnabled = legacyData.wikipediaSearch.enabled,
            wikipediaSearchImages = legacyData.wikipediaSearch.images,
        )
    }

    private fun makeGestureSettings(
        gesture: LegacySettings.GestureSettings.GestureAction,
        key: String
    ): GestureAction {
        return when (gesture) {
            LegacySettings.GestureSettings.GestureAction.OpenSearch -> GestureAction.Search
            LegacySettings.GestureSettings.GestureAction.OpenNotificationDrawer -> GestureAction.Notifications
            LegacySettings.GestureSettings.GestureAction.LockScreen -> GestureAction.ScreenLock
            LegacySettings.GestureSettings.GestureAction.OpenQuickSettings -> GestureAction.QuickSettings
            LegacySettings.GestureSettings.GestureAction.OpenRecents -> GestureAction.Recents
            LegacySettings.GestureSettings.GestureAction.OpenPowerDialog -> GestureAction.PowerMenu
            LegacySettings.GestureSettings.GestureAction.LaunchApp -> GestureAction.Launch(
                key
            )

            else -> GestureAction.NoAction
        }
    }
}