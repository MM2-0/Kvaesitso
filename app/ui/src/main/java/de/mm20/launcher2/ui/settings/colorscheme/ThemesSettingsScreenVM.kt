package de.mm20.launcher2.ui.settings.colorscheme

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.ByteString
import de.mm20.launcher2.ktx.toBytes
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.Theme
import de.mm20.launcher2.themes.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class ThemesSettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val dataStore: LauncherDataStore by inject()

    val selectedTheme: Flow<UUID?> = dataStore.data.map {
        it.appearance.themeId?.takeIf { it.isNotEmpty() }?.let {
            UUID.fromString(it)
        } ?: DefaultThemeId
    }
    val themes: Flow<List<Theme>> = themeRepository.getThemes()

    fun getTheme(id: UUID): Flow<Theme?> {
        return themeRepository.getTheme(id)
    }

    fun updateTheme(theme: Theme) {
        themeRepository.updateTheme(theme)
    }

    fun selectTheme(theme: Theme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(
                        it.appearance.toBuilder()
                            .setThemeId(theme.id.toString())
                    )
                    .build()
            }
        }
    }
}