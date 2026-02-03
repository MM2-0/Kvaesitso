package de.mm20.launcher2.ui.settings.transparencies

import android.content.Context
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class TransparencySchemesSettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()

    val selectedTransparencies = uiSettings.transparenciesId
    val transparencies: Flow<List<Transparencies>> = themeRepository.transparencies.getAll()

    fun getTransparencies(id: UUID): Flow<Transparencies?> {
        return themeRepository.transparencies.get(id)
    }

    fun updateTransparencies(transparencies: Transparencies) {
        themeRepository.transparencies.update(transparencies)
    }

    fun selectTransparencies(transparencies: Transparencies) {
        uiSettings.setTransparenciesId(transparencies.id)
    }

    fun duplicate(transparencies: Transparencies) {
        themeRepository.transparencies.create(transparencies.copy(id = UUID.randomUUID()))
    }

    fun delete(transparencies: Transparencies) {
        themeRepository.transparencies.delete(transparencies)
    }

    fun createNew(context: Context): UUID {
        val uuid = UUID.randomUUID()
        themeRepository.transparencies.create(
            Transparencies(
                id = uuid,
                name = context.getString(R.string.new_theme_name)
            )
        )
        return uuid
    }
}