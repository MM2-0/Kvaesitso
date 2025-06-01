package de.mm20.launcher2.ui.settings.colorscheme

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.ColorsDescriptor
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.BlackAndWhiteThemeId
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.Colors
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.toLegacyJson
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID

class ColorSchemesSettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()

    val selectedColors = uiSettings.colors.map {
        when(it) {
            ColorsDescriptor.Default -> DefaultThemeId
            ColorsDescriptor.BlackAndWhite -> BlackAndWhiteThemeId
            is ColorsDescriptor.Custom -> UUID.fromString(it.id)
        }
    }
    val colors: Flow<List<Colors>> = themeRepository.getAllColors()

    fun getTheme(id: UUID): Flow<Colors?> {
        return themeRepository.getColors(id)
    }

    fun updateTheme(colors: Colors) {
        themeRepository.updateColors(colors)
    }

    fun selectTheme(colors: Colors) {
        uiSettings.setColors(when(colors.id) {
            DefaultThemeId -> ColorsDescriptor.Default
            BlackAndWhiteThemeId -> ColorsDescriptor.BlackAndWhite
            else -> ColorsDescriptor.Custom(colors.id.toString())
        })
    }

    fun duplicate(colors: Colors) {
        themeRepository.createColors(colors.copy(id = UUID.randomUUID()))
    }

    fun delete(colors: Colors) {
        themeRepository.deleteColors(colors)
    }

    fun exportTheme(context: Context, colors: Colors) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "${colors.name}.kvtheme")
                file.writeText(colors.toLegacyJson())
                file
            }
            context.tryStartActivity(Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".fileprovider",
                    file
                ))
            }.let { Intent.createChooser(it, null) })
        }
    }

    fun createNew(context: Context) {
        themeRepository.createColors(
            Colors(
                id = UUID.randomUUID(),
                name = context.getString(R.string.new_color_scheme_name)
            )
        )
    }
}