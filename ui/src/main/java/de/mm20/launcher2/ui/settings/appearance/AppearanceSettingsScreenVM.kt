package de.mm20.launcher2.ui.settings.appearance

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.*
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.*
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import kotlinx.coroutines.flow.map
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

    val installedIconPacks: LiveData<List<IconPack>> = liveData {
        emit(
            listOf(
                IconPack(
                    name = "System",
                    packageName = "",
                    version = "",
                )
            ) +
                    iconRepository.getInstalledIconPacks()
        )
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

    val lightStatusBar = dataStore.data.map { it.systemBars.lightStatusBar }.asLiveData()
    fun setLightStatusBar(lightStatusBar: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSystemBars(
                        it.systemBars.toBuilder()
                            .setLightStatusBar(lightStatusBar)
                    )
                    .build()
            }
        }
    }

    val lightNavBar = dataStore.data.map { it.systemBars.lightNavBar }.asLiveData()
    fun setLightNavBar(lightNavBar: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSystemBars(
                        it.systemBars.toBuilder()
                            .setLightNavBar(lightNavBar)
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

    val layout = dataStore.data.map { it.appearance.layout }.asLiveData()
    fun setLayout(layout: Settings.AppearanceSettings.Layout) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(
                        it.appearance.toBuilder()
                            .setLayout(layout)
                    )
                    .build()
            }
        }
    }
}