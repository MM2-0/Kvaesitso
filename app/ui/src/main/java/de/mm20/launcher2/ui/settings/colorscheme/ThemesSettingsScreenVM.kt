package de.mm20.launcher2.ui.settings.colorscheme

import android.util.Log
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.Theme
import de.mm20.launcher2.themes.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class ThemesSettingsScreenVM: ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()

    val selectedTheme: Flow<UUID?> = flowOf(DefaultThemeId)
    val themes: Flow<List<Theme>> = themeRepository.getThemes()

    fun getTheme(id: UUID): Flow<Theme?> {
        return themeRepository.getTheme(id)
    }

    fun updateTheme(theme: Theme) {
        Log.d("MM20", "updateTheme: $theme")
        themeRepository.updateTheme(theme)
    }
}