package de.mm20.launcher2.ui.settings.appearance

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Font
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.preferences.Settings.SystemBarsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppearanceSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private val iconRepository: IconRepository by inject()

    val theme = dataStore.data.map { it.appearance.theme }.asLiveData()
    fun setTheme(theme: Theme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setTheme(theme))
                    .build()
            }
        }
    }

    val colorScheme = dataStore.data.map { it.appearance.colorScheme }.asLiveData()
    fun setColorScheme(colorScheme: ColorScheme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setColorScheme(colorScheme))
                    .build()
            }
        }
    }

    val font = dataStore.data.map { it.appearance.font }.asLiveData()
    fun setFont(font: Font) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setFont(font))
                    .build()
            }
        }
    }

    val columnCount = dataStore.data.map { it.grid.columnCount }.asLiveData()
    fun setColumnCount(columnCount: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setGrid(it.grid.toBuilder().setColumnCount(columnCount))
                    .build()
            }
        }
    }

    val iconSize = dataStore.data.map { it.grid.iconSize }.asLiveData()
    fun setIconSize(iconSize: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setGrid(it.grid.toBuilder().setIconSize(iconSize))
                    .build()
            }
        }
    }


    val showLabels = dataStore.data.map { it.grid.showLabels }.asLiveData()
    fun setShowLabels(showLabels: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setGrid(it.grid.toBuilder().setShowLabels(showLabels))
                    .build()
            }
        }
    }

    val dimWallpaper = dataStore.data.map { it.appearance.dimWallpaper }.asLiveData()
    fun setDimWallpaper(dimWallpaper: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setDimWallpaper(dimWallpaper))
                    .build()
            }
        }
    }

    fun isBlurAvailable(context: Context): Boolean {
        if (!isAtLeastApiLevel(31)) return false
        return context.getSystemService<WindowManager>()?.isCrossWindowBlurEnabled == true
    }

    val blurWallpaper = dataStore.data.map { it.appearance.blurWallpaper }.asLiveData()
    fun setBlurWallpaper(blurWallpaper: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setBlurWallpaper(blurWallpaper))
                    .build()
            }
        }
    }

    fun openWallpaperChooser(context: AppCompatActivity) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
    }

    val searchBarStyle = dataStore.data.map { it.searchBar.searchBarStyle }.asLiveData()
    fun setSearchBarStyle(searchBarStyle: SearchBarSettings.SearchBarStyle) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSearchBar(
                        it.searchBar.toBuilder()
                            .setSearchBarStyle(searchBarStyle)
                    )
                    .build()
            }
        }
    }

    val iconShape = dataStore.data.map { it.icons.shape }.asLiveData()
    fun setIconShape(iconShape: Settings.IconSettings.IconShape) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setShape(iconShape)
                    )
                    .build()
            }
        }
    }

    val adaptifyLegacyIcons = dataStore.data.map { it.icons.adaptify }.asLiveData()
    fun setAdaptifyLegacyIcons(adaptify: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setAdaptify(adaptify)
                    )
                    .build()
            }
        }
    }

    val themedIcons = dataStore.data.map { it.icons.themedIcons }.asLiveData()
    fun setThemedIcons(themedIcons: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setThemedIcons(themedIcons)
                    )
                    .build()
            }
        }
    }

    val forceThemedIcons = dataStore.data.map { it.icons.forceThemed }.asLiveData()
    fun setForceThemedIcons(forceThemedIcons: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setForceThemed(forceThemedIcons)
                    )
                    .build()
            }
        }
    }

    val installedIconPacks: Flow<List<IconPack>> = iconRepository.getInstalledIconPacks().map {
        listOf(
            IconPack(
                name = "System",
                packageName = "",
                version = "",
            )
        ) + it
    }

    val iconPackThemed = dataStore.data.map { it.icons.iconPackThemed }
    fun setIconPackThemed(iconPackThemed: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons
                            .toBuilder()
                            .setIconPackThemed(iconPackThemed)
                    )
                    .build()
            }
        }
    }

    val iconPack = dataStore.data.map { it.icons.iconPack }.asLiveData()
    fun setIconPack(iconPack: String) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setIconPack(iconPack)
                    )
                    .build()
            }
        }
    }

    val searchBarColor = dataStore.data.map { it.searchBar.color }.asLiveData()
    fun setSearchBarColor(color: SearchBarColors) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSearchBar(
                        it.searchBar.toBuilder()
                            .setColor(color)
                    )
                    .build()
            }
        }
    }

    val statusBarIcons = dataStore.data.map { it.systemBars.statusBarColor }.asLiveData()
    fun setLightStatusBar(statusBarColor: SystemBarsSettings.SystemBarColors) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSystemBars(
                        it.systemBars.toBuilder()
                            .setStatusBarColor(statusBarColor)
                    )
                    .build()
            }
        }
    }

    val navBarIcons = dataStore.data.map { it.systemBars.navBarColor }.asLiveData()
    fun setLightNavBar(navBarColors: SystemBarsSettings.SystemBarColors) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSystemBars(
                        it.systemBars.toBuilder()
                            .setNavBarColor(navBarColors)
                    )
                    .build()
            }
        }
    }

    val hideStatusBar = dataStore.data.map { it.systemBars.hideStatusBar }.asLiveData()
    fun setHideStatusBar(hideStatusBar: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSystemBars(
                        it.systemBars.toBuilder()
                            .setHideStatusBar(hideStatusBar)
                    )
                    .build()
            }
        }
    }

    val hideNavBar = dataStore.data.map { it.systemBars.hideNavBar }.asLiveData()
    fun setHideNavBar(hideNavBar: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSystemBars(
                        it.systemBars.toBuilder()
                            .setHideNavBar(hideNavBar)
                    )
                    .build()
            }
        }
    }
}