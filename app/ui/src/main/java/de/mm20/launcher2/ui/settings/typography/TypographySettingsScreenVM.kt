package de.mm20.launcher2.ui.settings.typography

import android.content.Context
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.typography.Typography
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class TypographySettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()

    val selectedTypography = uiSettings.typographyId
    val typography: Flow<List<Typography>> = themeRepository.typographies.getAll()

    fun getTypography(id: UUID): Flow<Typography?> {
        return themeRepository.typographies.get(id)
    }

    fun updateTypography(typography: Typography) {
        themeRepository.typographies.update(typography)
    }

    fun selectTypography(typography: Typography) {
        uiSettings.setTypographyId(typography.id)
    }

    fun duplicate(typography: Typography) {
        themeRepository.typographies.create(typography.copy(id = UUID.randomUUID()))
    }

    fun delete(typography: Typography) {
        themeRepository.typographies.delete(typography)
    }

    fun createNew(context: Context) {
        themeRepository.typographies.create(
            Typography(
                id = UUID.randomUUID(),
                name = context.getString(R.string.new_theme_name)
            )
        )
    }
}