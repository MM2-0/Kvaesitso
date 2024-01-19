package de.mm20.launcher2.preferences

import android.content.Context
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LegacySettings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.preferences.ktx.toSettingsColorsScheme
import scheme.Scheme
import java.util.UUID

fun createFactorySettings(context: Context): LegacySettings {
    return LegacySettings.newBuilder()
        .setAppearance(
            LegacySettings.AppearanceSettings
                .newBuilder()
                .setTheme(LegacySettings.AppearanceSettings.Theme.System)
                .setDimWallpaper(false)
                .setBlurWallpaper(true)
                .setBlurWallpaperRadius(32)
                .setThemeId(UUID(0L, 0L).toString())
                .setFont(LegacySettings.AppearanceSettings.Font.Outfit)
                .build()
        )
        .setWeather(
            LegacySettings.WeatherSettings
                .newBuilder()
                .setProvider(LegacySettings.WeatherSettings.WeatherProvider.MetNo)
                .setImperialUnits(context.resources.getBoolean(R.bool.default_imperialUnits))
                .build()
        )
        .setMusicWidget(
            LegacySettings.MusicWidgetSettings
                .newBuilder()
                .build()
        )
        .setCalendarWidget(
            LegacySettings.CalendarWidgetSettings
                .newBuilder()
                .setHideAlldayEvents(false)
        )
        .setClockWidget(
            LegacySettings.ClockWidgetSettings
                .newBuilder()
                .setLayout(LegacySettings.ClockWidgetSettings.ClockWidgetLayout.Vertical)
                .setClockStyle(LegacySettings.ClockWidgetSettings.ClockStyle.DigitalClock1)
                .setColor(LegacySettings.ClockWidgetSettings.ClockWidgetColors.Auto)
                .setAlarmPart(true)
                .setBatteryPart(true)
                .setMusicPart(true)
                .setDatePart(true)
                .setFavoritesPart(false)
                .setFillHeight(true)
                .setAlignment(LegacySettings.ClockWidgetSettings.ClockWidgetAlignment.Bottom)
                .build()
        )
        .setFavorites(
            LegacySettings.FavoritesSettings
                .newBuilder()
                .setEnabled(true)
                .setFrequentlyUsed(true)
                .setFrequentlyUsedRows(1)
                .setEditButton(true)
        )
        .setFileSearch(
            LegacySettings.FilesSearchSettings
                .newBuilder()
                .setLocalFiles(true)
                .setNextcloud(false)
                .setGdrive(false)
                .setOnedrive(false)
                .setNextcloud(false)
        )
        .setContactsSearch(
            LegacySettings.ContactsSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setCalendarSearch(
            LegacySettings.CalendarSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setAppShortcutSearch(
            LegacySettings.AppShortcutSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setCalculatorSearch(
            LegacySettings.CalculatorSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setUnitConverterSearch(
            LegacySettings.UnitConverterSearchSettings
                .newBuilder()
                .setEnabled(true)
                .setCurrencies(true)
        )
        .setWikipediaSearch(
            LegacySettings.WikipediaSearchSettings
                .newBuilder()
                .setEnabled(false)
                .setImages(false)
                .setCustomUrl("")
        )
        .setWebsiteSearch(
            LegacySettings.WebsiteSearchSettings
                .newBuilder()
                .setEnabled(false)
        )
        .setWebSearch(
            LegacySettings.WebSearchSettings
                .newBuilder()
                .setEnabled(true)
        )
        .setBadges(
            LegacySettings.BadgeSettings
                .newBuilder()
                .setNotifications(true)
                .setCloudFiles(true)
                .setShortcuts(true)
                .setSuspendedApps(true)
        )
        .setGrid(
            LegacySettings.GridSettings.newBuilder()
                .setColumnCount(context.resources.getInteger(R.integer.config_columnCount))
                .setIconSize(48)
                .setShowLabels(true)
                .build()
        )
        .setSearchBar(
            LegacySettings.SearchBarSettings.newBuilder()
                .setSearchBarStyle(LegacySettings.SearchBarSettings.SearchBarStyle.Transparent)
                .setAutoFocus(true)
                .setLaunchOnEnter(true)
                .setColor(SearchBarColors.Auto)
                .setHiddenItemsButton(true)
                .build()
        )
        .setIcons(
            LegacySettings.IconSettings.newBuilder()
                .setAdaptify(true)
                .setShape(LegacySettings.IconSettings.IconShape.PlatformDefault)
                .setThemedIcons(false)
                .setIconPack("")
                .setIconPackThemed(true)
        )
        .setEasterEgg(false)
        .setSystemBars(
            LegacySettings.SystemBarsSettings.newBuilder()
                .setNavBarColor(LegacySettings.SystemBarsSettings.SystemBarColors.Auto)
                .setStatusBarColor(LegacySettings.SystemBarsSettings.SystemBarColors.Auto)
                .setHideStatusBar(false)
                .setHideNavBar(false)
        )
        .setCards(
            LegacySettings.CardSettings.newBuilder()
                .setBorderWidth(0)
                .setRadius(12)
                .setOpacity(1f)
        )
        .setWidgets(
            LegacySettings.WidgetSettings.newBuilder()
                .setEditButton(true)
        )
        .setLayout(
            LegacySettings.LayoutSettings.newBuilder()
                .setBaseLayout(LegacySettings.LayoutSettings.Layout.PullDown)
                .setBottomSearchBar(false)
                .setReverseSearchResults(false)
                .setFixedRotation(false)
        )
        .setGestures(
            LegacySettings.GestureSettings.newBuilder()
                .setDoubleTap(
                    if (isAtLeastApiLevel(28)) {
                        LegacySettings.GestureSettings.GestureAction.LockScreen
                    } else {
                        LegacySettings.GestureSettings.GestureAction.None
                    })
                .setLongPress(LegacySettings.GestureSettings.GestureAction.None)
                .setSwipeDown(LegacySettings.GestureSettings.GestureAction.OpenNotificationDrawer)
                .setSwipeLeft(LegacySettings.GestureSettings.GestureAction.None)
                .setSwipeRight(LegacySettings.GestureSettings.GestureAction.None)
        )
        .setResultOrdering(
            LegacySettings.SearchResultOrderingSettings.newBuilder()
                .setOrdering(LegacySettings.SearchResultOrderingSettings.Ordering.Weighted)
                .setWeightFactor(LegacySettings.SearchResultOrderingSettings.WeightFactor.Default)
        )
        .setAnimations(
            LegacySettings.AnimationSettings.newBuilder()
                .setCharging(true)
        )
        .build()
}

internal val DefaultCustomColorsBase: LegacySettings.AppearanceSettings.CustomColors.BaseColors
    get() {
        val scheme = Scheme.light(0xFFACE330.toInt())
        return LegacySettings.AppearanceSettings.CustomColors.BaseColors.newBuilder()
            .setAccent1(scheme.primary)
            .setAccent2(scheme.secondary)
            .setAccent3(scheme.tertiary)
            .setNeutral1(scheme.surface)
            .setNeutral2(scheme.surfaceVariant)
            .setError(scheme.error)
            .build()
    }

internal val DefaultLightCustomColorScheme: LegacySettings.AppearanceSettings.CustomColors.Scheme
    get() {
        val scheme = Scheme.light(0xFFACE330.toInt())
        return scheme.toSettingsColorsScheme()
    }

internal val DefaultDarkCustomColorScheme: LegacySettings.AppearanceSettings.CustomColors.Scheme
    get() {
        val scheme = Scheme.dark(0xFFACE330.toInt())
        return scheme.toSettingsColorsScheme()
    }