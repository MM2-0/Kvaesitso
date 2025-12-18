package de.mm20.launcher2.ui.settings.appearance

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeBundle
import de.mm20.launcher2.themes.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ImportThemeSettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository by inject<ThemeRepository>()
    private val uiSettings by inject<UiSettings>()

    var themeBundle by mutableStateOf<ThemeBundle?>(null)
        private set

    var colorsExists by mutableStateOf(false)
        private set

    var typographyExists by mutableStateOf(false)
        private set

    var shapesExists by mutableStateOf(false)
        private set

    var transparenciesExists by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf(false)
        private set

    var applyTheme by mutableStateOf(true)

    fun init(context: Context, fromUri: Uri) {
        themeBundle = null
        error = false
        applyTheme = true
        loading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(fromUri)?.reader()?.use {
                    val text = it.readText()
                    val theme = ThemeBundle.fromJson(text)
                    if (theme != null) {
                        val colors =
                            theme.colors?.id?.let { themeRepository.colors.get(it) }?.first()
                        val typography =
                            theme.typography?.id?.let { themeRepository.typographies.get(it) }?.first()
                        val shapes =
                            theme.shapes?.id?.let { themeRepository.shapes.get(it) }?.first()
                        val transparencies =
                            theme.transparencies?.id?.let { themeRepository.transparencies.get(it) }?.first()

                        colorsExists = colors != null
                        typographyExists = typography != null
                        shapesExists = shapes != null
                        transparenciesExists = transparencies != null
                        themeBundle = theme
                        loading = false
                    } else {
                        error = true
                    }
                }
            } catch (e: SecurityException) {
                CrashReporter.logException(e)
                error = true
            }
        }
    }

    fun import(): Job? {
        val themeBundle = this.themeBundle ?: return null

        val colors = themeBundle.colors
        val typography = themeBundle.typography
        val shapes = themeBundle.shapes
        val transparencies = themeBundle.transparencies

        val colorsExist = this.colorsExists
        val shapesExist = this.shapesExists
        val transparenciesExist = this.transparenciesExists

        loading = true
        return viewModelScope.launch {
            if (colors != null) {
                if (colorsExist) {
                    themeRepository.colors.update(colors)
                } else {
                    themeRepository.colors.create(colors)
                }
                if (applyTheme) {
                    uiSettings.setColorsId(colors.id)
                }
            }
            if (typography != null) {
                if (typographyExists) {
                    themeRepository.typographies.update(typography)
                } else {
                    themeRepository.typographies.create(typography)
                }
                if (applyTheme) {
                    uiSettings.setTypographyId(typography.id)
                }
            }
            if (shapes != null) {
                if (shapesExist) {
                    themeRepository.shapes.update(shapes)
                } else {
                    themeRepository.shapes.create(shapes)
                }
                if (applyTheme) {
                    uiSettings.setShapesId(shapes.id)
                }
            }
            if (transparencies != null) {
                if (transparenciesExist) {
                    themeRepository.transparencies.update(transparencies)
                } else {
                    themeRepository.transparencies.create(transparencies)
                }
                if (applyTheme) {
                    uiSettings.setTransparenciesId(transparencies.id)
                }
            }
            loading = false
        }
    }
}