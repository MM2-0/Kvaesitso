package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.preferences.Font
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.ScreenOrientation
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.SystemBarColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

data class CardStyle(
    val opacity: Float = 1f,
    val borderWidth: Int = 0,
)

data class GridSettings(
    val columnCount: Int = 5,
    val iconSize: Int = 48,
    val showLabels: Boolean = true,
    val showList: Boolean = false,
    val showListIcons: Boolean = true,
)

class UiSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val favoritesEnabled
        get() = launcherDataStore.data.map { it.favoritesEnabled || it.homeScreenDock }

    val iconShape
        get() = launcherDataStore.data.map {
            it.iconsShape
        }

    fun setIconShape(iconShape: IconShape) {
        launcherDataStore.update {
            it.copy(iconsShape = iconShape)
        }
    }

    val gridSettings
        get() = launcherDataStore.data.map {
            GridSettings(
                showLabels = it.gridLabels,
                showList = it.gridList,
                showListIcons = it.gridListIcons,
                iconSize = it.gridIconSize,
                columnCount = it.gridColumnCount,
            )
        }

    fun setGridColumnCount(columnCount: Int) {
        launcherDataStore.update {
            it.copy(gridColumnCount = columnCount)
        }
    }

    fun setGridIconSize(iconSize: Int) {
        launcherDataStore.update {
            it.copy(gridIconSize = iconSize)
        }
    }

    fun setGridShowLabels(showLabels: Boolean) {
        launcherDataStore.update {
            it.copy(gridLabels = showLabels)
        }
    }

    fun setGridShowList(showList: Boolean) {
        launcherDataStore.update {
            it.copy(gridList = showList)
        }
    }

    fun setGridShowListIcons(showIcons: Boolean) {
        launcherDataStore.update {
            it.copy(gridListIcons = showIcons)
        }
    }

    val cardStyle
        get() = launcherDataStore.data.map {
            CardStyle(
                opacity = it.surfacesOpacity,
                borderWidth = it.surfacesBorderWidth,
            )
        }

    fun setCardOpacity(opacity: Float) {
        launcherDataStore.update {
            it.copy(surfacesOpacity = opacity)
        }
    }

    fun setCardBorderWidth(borderWidth: Int) {
        launcherDataStore.update {
            it.copy(surfacesBorderWidth = borderWidth)
        }
    }

    val dimWallpaper
        get() = launcherDataStore.data.map {
            it.wallpaperDim
        }

    fun setDimWallpaper(dimWallpaper: Boolean) {
        launcherDataStore.update {
            it.copy(wallpaperDim = dimWallpaper)
        }
    }

    val blurWallpaper
        get() = launcherDataStore.data.map {
            it.wallpaperBlur
        }.distinctUntilChanged()

    fun setBlurWallpaper(blurWallpaper: Boolean) {
        launcherDataStore.update {
            it.copy(wallpaperBlur = blurWallpaper)
        }
    }

    val wallpaperBlurRadius
        get() = launcherDataStore.data.map {
            it.wallpaperBlurRadius
        }.distinctUntilChanged()

    fun setWallpaperBlurRadius(wallpaperBlurRadius: Int) {
        launcherDataStore.update {
            it.copy(wallpaperBlurRadius = wallpaperBlurRadius)
        }
    }

    val colorScheme
        get() = launcherDataStore.data.map {
            it.uiColorScheme
        }.distinctUntilChanged()

    val compatModeColors
        get() = launcherDataStore.data.map {
            it.uiCompatModeColors
        }.distinctUntilChanged()

    fun setCompatModeColors(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(uiCompatModeColors = enabled)
        }
    }

    val statusBarColor
        get() = launcherDataStore.data.map {
            it.systemBarsStatusColors
        }.distinctUntilChanged()

    val hideStatusBar
        get() = launcherDataStore.data.map {
            it.systemBarsHideStatus
        }.distinctUntilChanged()

    val hideNavigationBar
        get() = launcherDataStore.data.map {
            it.systemBarsHideNav
        }.distinctUntilChanged()

    fun setHideStatusBar(hideStatusBar: Boolean) {
        launcherDataStore.update {
            it.copy(systemBarsHideStatus = hideStatusBar)
        }
    }

    fun setHideNavigationBar(hideNavigationBar: Boolean) {
        launcherDataStore.update {
            it.copy(systemBarsHideNav = hideNavigationBar)
        }
    }

    val navigationBarColor
        get() = launcherDataStore.data.map {
            it.systemBarsNavColors
        }.distinctUntilChanged()

    fun setStatusBarColor(statusBarColor: SystemBarColors) {
        launcherDataStore.update {
            it.copy(systemBarsStatusColors = statusBarColor)
        }
    }

    fun setNavigationBarColor(navigationBarColor: SystemBarColors) {
        launcherDataStore.update {
            it.copy(systemBarsNavColors = navigationBarColor)
        }
    }

    val chargingAnimation
        get() = launcherDataStore.data.map {
            it.animationsCharging
        }.distinctUntilChanged()

    fun setChargingAnimation(chargingAnimation: Boolean) {
        launcherDataStore.update {
            it.copy(animationsCharging = chargingAnimation)
        }
    }

    val clockFillScreen
        get() = launcherDataStore.data.map {
            it.homeScreenWidgets
        }.distinctUntilChanged()

    val searchBarStyle
        get() = launcherDataStore.data.map {
            it.searchBarStyle
        }.distinctUntilChanged()

    fun setSearchBarStyle(searchBarStyle: SearchBarStyle) {
        launcherDataStore.update {
            it.copy(searchBarStyle = searchBarStyle)
        }
    }

    val searchBarColor
        get() = launcherDataStore.data.map {
            it.searchBarColors
        }.distinctUntilChanged()

    fun setSearchBarColor(color: SearchBarColors) {
        launcherDataStore.update {
            it.copy(searchBarColors = color)
        }
    }

    val bottomSearchBar
        get() = launcherDataStore.data.map {
            it.searchBarBottom
        }.distinctUntilChanged()

    fun setBottomSearchBar(bottomSearchBar: Boolean) {
        launcherDataStore.update {
            it.copy(searchBarBottom = bottomSearchBar)
        }
    }

    val reverseSearchResults
        get() = launcherDataStore.data.map {
            it.searchResultsReversed
        }.distinctUntilChanged()

    fun setReverseSearchResults(reverseSearchResults: Boolean) {
        launcherDataStore.update {
            it.copy(searchResultsReversed = reverseSearchResults)
        }
    }

    val fixedSearchBar
        get() = launcherDataStore.data.map {
            it.searchBarFixed
        }.distinctUntilChanged()

    fun setFixedSearchBar(fixedSearchBar: Boolean) {
        launcherDataStore.update {
            it.copy(searchBarFixed = fixedSearchBar)
        }
    }

    val openKeyboardOnSearch
        get() = launcherDataStore.data.map {
            it.searchBarKeyboard
        }.distinctUntilChanged()


    val orientation
        get() = launcherDataStore.data.map {
            it.uiOrientation
        }.distinctUntilChanged()

    fun setOrientation(orientation: ScreenOrientation) {
        launcherDataStore.update {
            it.copy(uiOrientation = orientation)
        }
    }


    val colorsId
        get() = launcherDataStore.data.map {
            it.uiColorsId
        }.distinctUntilChanged()

    fun setColorsId(colorsId: UUID) {
        launcherDataStore.update {
            it.copy(uiColorsId = colorsId)
        }
    }

    val shapesId
        get() = launcherDataStore.data.map {
            it.uiShapesId
        }.distinctUntilChanged()

    fun setShapesId(shapesId: UUID) {
        launcherDataStore.update {
            it.copy(uiShapesId = shapesId)
        }
    }

    val transparenciesId
        get() = launcherDataStore.data.map {
            it.uiTransparenciesId
        }.distinctUntilChanged()

    fun setTransparenciesId(transparenciesId: UUID) {
        launcherDataStore.update {
            it.copy(uiTransparenciesId = transparenciesId)
        }
    }

    val typographyId
        get() = launcherDataStore.data.map {
            it.uiTypographyId
        }.distinctUntilChanged()

    fun setTypographyId(typographyId: UUID) {
        launcherDataStore.update {
            it.copy(uiTypographyId = typographyId)
        }
    }

    val font
        get() = launcherDataStore.data.map {
            it.uiFont
        }.distinctUntilChanged()

    fun setFont(font: Font) {
        launcherDataStore.update {
            it.copy(uiFont = font)
        }
    }

    fun setColorScheme(colorScheme: ColorScheme) {
        launcherDataStore.update {
            it.copy(uiColorScheme = colorScheme)
        }
    }

    val dock
        get() = launcherDataStore.data.map {
            it.homeScreenDock
        }.distinctUntilChanged()

    fun setDock(dock: Boolean) {
        launcherDataStore.update {
            it.copy(homeScreenDock = dock)
        }
    }

    val dockRows
        get() = launcherDataStore.data.map {
            it.homeScreenDockRows
        }.distinctUntilChanged()

    fun setDockRows(rows: Int) {
        launcherDataStore.update {
            it.copy(homeScreenDockRows = rows)
        }
    }

    val homeScreenWidgets
        get() = launcherDataStore.data.map {
            it.homeScreenWidgets
        }.distinctUntilChanged()

    fun setHomeScreenWidgets(widgets: Boolean) {
        launcherDataStore.update {
            it.copy(homeScreenWidgets = widgets)
        }
    }

    val widgetEditButton
        get() = launcherDataStore.data.map {
            it.widgetsEditButton
        }.distinctUntilChanged()

    fun setWidgetEditButton(editButton: Boolean) {
        launcherDataStore.update {
            it.copy(widgetsEditButton = editButton)
        }
    }
}
