package de.mm20.launcher2.ui.settings.appearance

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.Settings.IconSettings.LegacyIconBackground
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

    val legacyIconBackground = dataStore.data.map { it.icons.legacyIconBg }.asLiveData()
    fun setLegacyIconBackground(legacyIconBackground: LegacyIconBackground) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setLegacyIconBg(legacyIconBackground)
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

    val installedIconPacks: LiveData<List<IconPack>> = liveData {
        emit(
            listOf(IconPack(
                name = "System",
                packageName = "",
                version = "",
            )) +
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
}