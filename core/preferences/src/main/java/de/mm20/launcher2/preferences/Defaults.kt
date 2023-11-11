package de.mm20.launcher2.preferences

import android.content.Context
import androidx.core.graphics.blue
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.preferences.ktx.toSettingsColorsScheme
import scheme.Scheme
import java.util.UUID

fun createFactorySettings(context: Context): Settings {
    return Settings.newBuilder()
        .setAppearance(
            Settings.AppearanceSettings
                .newBuilder()
                .setTheme(Settings.AppearanceSettings.Theme.System)
                .setDimWallpaper(false)
                .setBlurWallpaper(true)
                .setBlurWallpaperRadius(32)
                .setThemeId(UUID(0L, 0L).toString())
                .setFont(Settings.AppearanceSettings.Font.Outfit)
                .build()
        )
        .setWeather(
            Settings.WeatherSettings
                .newBuilder()
                .setProvider(Settings.WeatherSettings.WeatherProvider.MetNo)
                .setImperialUnits(context.resources.getBoolean(R.bool.default_imperialUnits))
                .build()
        )
        .setMusicWidget(
            Settings.MusicWidgetSettings
                .newBuilder()
                .build()
        )
        .setCalendarWidget(
            Settings.CalendarWidgetSettings
                .newBuilder()
                .setHideAlldayEvents(false)
        )
        .setClockWidget(
            Settings.ClockWidgetSettings
                .newBuilder()
                .setLayout(Settings.ClockWidgetSettings.ClockWidgetLayout.Vertical)
                .setClockStyle(Settings.ClockWidgetSettings.ClockStyle.DigitalClock1)
                .setColor(Settings.ClockWidgetSettings.ClockWidgetColors.Auto)
                .setAlarmPart(true)
                .setBatteryPart(true)
                .setMusicPart(true)
                .setDatePart(true)
                .setFavoritesPart(false)
                .setFillHeight(true)
                .setAlignment(Settings.ClockWidgetSettings.ClockWidgetAlignment.Bottom)
                .build()
        )
        .setFavorites(
            Settings.FavoritesSettings
                .newBuilder()
                .setEnabled(true)
                .setFrequentlyUsed(true)
                .setFrequentlyUsedRows(1)
                .setEditButton(true)
        )
        .setFileSearch(
            Settings.FilesSearchSettings
                .newBuilder()
                .setLocalFiles(true)
                .setNextcloud(false)
                .setGdrive(false)
                .setOnedrive(false)
                .setNextcloud(false)
        )
        .setContactsSearch(
            Settings.ContactsSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setCalendarSearch(
            Settings.CalendarSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setAppShortcutSearch(
            Settings.AppShortcutSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setCalculatorSearch(
            Settings.CalculatorSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setUnitConverterSearch(
            Settings.UnitConverterSearchSettings
                .newBuilder()
                .setEnabled(true)
                .setCurrencies(true)
        )
        .setWikipediaSearch(
            Settings.WikipediaSearchSettings
                .newBuilder()
                .setEnabled(false)
                .setImages(false)
                .setCustomUrl("")
        )
        .setWebsiteSearch(
            Settings.WebsiteSearchSettings
                .newBuilder()
                .setEnabled(false)
        )
        .setWebSearch(
            Settings.WebSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setBadges(
            Settings.BadgeSettings
                .newBuilder()
                .setNotifications(true)
                .setCloudFiles(true)
                .setShortcuts(true)
                .setSuspendedApps(true)
        )
        .setGrid(
            Settings.GridSettings.newBuilder()
                .setColumnCount(context.resources.getInteger(R.integer.config_columnCount))
                .setIconSize(48)
                .setShowLabels(true)
                .build()
        )
        .setSearchBar(
            Settings.SearchBarSettings.newBuilder()
                .setSearchBarStyle(Settings.SearchBarSettings.SearchBarStyle.Transparent)
                .setAutoFocus(true)
                .setLaunchOnEnter(true)
                .setColor(SearchBarColors.Auto)
                .setHiddenItemsButton(true)
                .build()
        )
        .setIcons(
            Settings.IconSettings.newBuilder()
                .setAdaptify(true)
                .setShape(Settings.IconSettings.IconShape.PlatformDefault)
                .setThemedIcons(false)
                .setIconPack("")
                .setIconPackThemed(true)
        )
        .setEasterEgg(false)
        .setSystemBars(
            Settings.SystemBarsSettings.newBuilder()
                .setNavBarColor(Settings.SystemBarsSettings.SystemBarColors.Auto)
                .setStatusBarColor(Settings.SystemBarsSettings.SystemBarColors.Auto)
                .setHideStatusBar(false)
                .setHideNavBar(false)
        )
        .setCards(
            Settings.CardSettings.newBuilder()
                .setBorderWidth(0)
                .setRadius(12)
                .setOpacity(1f)
        )
        .setWidgets(
            Settings.WidgetSettings.newBuilder()
                .setEditButton(true)
        )
        .setLayout(
            Settings.LayoutSettings.newBuilder()
                .setBaseLayout(Settings.LayoutSettings.Layout.PullDown)
                .setBottomSearchBar(false)
                .setReverseSearchResults(false)
                .setFixedRotation(false)
        )
        .setGestures(
            Settings.GestureSettings.newBuilder()
                .setDoubleTap(
                    if (isAtLeastApiLevel(28)) {
                        Settings.GestureSettings.GestureAction.LockScreen
                    } else {
                        Settings.GestureSettings.GestureAction.None
                    })
                .setLongPress(Settings.GestureSettings.GestureAction.None)
                .setSwipeDown(Settings.GestureSettings.GestureAction.OpenNotificationDrawer)
                .setSwipeLeft(Settings.GestureSettings.GestureAction.None)
                .setSwipeRight(Settings.GestureSettings.GestureAction.None)
        )
        .setResultOrdering(
            Settings.SearchResultOrderingSettings.newBuilder()
                .setOrdering(Settings.SearchResultOrderingSettings.Ordering.Weighted)
                .setWeightFactor(Settings.SearchResultOrderingSettings.WeightFactor.Default)
        )
        .setAnimations(
            Settings.AnimationSettings.newBuilder()
                .setCharging(true)
        )
        .setLocationsSearch(
            Settings.LocationsSearchSettings.newBuilder()
                .setEnabled(false)
                .setUseInsaneUnits(context.resources.getBoolean(R.bool.default_imperialUnits))
                .setSearchRadius(1500)
                .setCustomOverpassUrl("https://overpass-api.de")
                .setShowMap(false)
                .setShowPositionOnMap(false)
                .setCustomTileServerUrl("https://tile.openstreetmap.org")
        )
        .build()
}

internal val DefaultCustomColorsBase: Settings.AppearanceSettings.CustomColors.BaseColors
    get() {
        val scheme = Scheme.light(0xFFACE330.toInt())
        return Settings.AppearanceSettings.CustomColors.BaseColors.newBuilder()
            .setAccent1(scheme.primary)
            .setAccent2(scheme.secondary)
            .setAccent3(scheme.tertiary)
            .setNeutral1(scheme.surface)
            .setNeutral2(scheme.surfaceVariant)
            .setError(scheme.error)
            .build()
    }

internal val DefaultLightCustomColorScheme: Settings.AppearanceSettings.CustomColors.Scheme
    get() {
        val scheme = Scheme.light(0xFFACE330.toInt())
        return scheme.toSettingsColorsScheme()
    }

internal val DefaultDarkCustomColorScheme: Settings.AppearanceSettings.CustomColors.Scheme
    get() {
        val scheme = Scheme.dark(0xFFACE330.toInt())
        return scheme.toSettingsColorsScheme()
    }