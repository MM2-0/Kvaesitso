package de.mm20.launcher2.ui.settings.appearance

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.ColorsDescriptor
import de.mm20.launcher2.preferences.ShapesDescriptor
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeBundle
import de.mm20.launcher2.themes.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class ImportThemeSettingsScreenVM: ViewModel(), KoinComponent {

    private val themeRepository by inject<ThemeRepository>()
    private val uiSettings by inject<UiSettings>()

    var themeBundle by mutableStateOf<ThemeBundle?>(null)
        private set

    var colorsExists by mutableStateOf(false)
        private set

    var shapesExists by mutableStateOf(false)
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
                        val colors = theme.colors?.id?.let { themeRepository.getColors(it) }?.first()
                        val shapes = theme.shapes?.id?.let { themeRepository.getShapes(it) }?.first()

                        colorsExists = colors != null
                        shapesExists = shapes != null
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
        val shapes = themeBundle.shapes

        val colorsExist = this.colorsExists
        val shapesExist = this.shapesExists

        loading = true
        return viewModelScope.launch {
            if (colors != null) {
                if (colorsExist) {
                    themeRepository.updateColors(colors)
                } else {
                    themeRepository.createColors(colors)
                }
                if (applyTheme) {
                    uiSettings.setColors(ColorsDescriptor.Custom(colors.id.toString()))
                }
            }
            if (shapes != null) {
                if (shapesExist) {
                    themeRepository.updateShapes(shapes)
                } else {
                    themeRepository.createShapes(shapes)
                }
                if (applyTheme) {
                    uiSettings.setShapes(ShapesDescriptor.Custom(shapes.id.toString()))
                }
            }
            loading = false
        }
    }
}