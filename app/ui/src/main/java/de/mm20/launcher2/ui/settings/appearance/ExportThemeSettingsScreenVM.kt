package de.mm20.launcher2.ui.settings.appearance

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.themes.shapes.Shapes
import de.mm20.launcher2.themes.ThemeBundle
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.themes.typography.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class ExportThemeSettingsScreenVM: ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()

    val colorSchemes = themeRepository.colors.getAll().map { it.filter { !it.builtIn } }
    val typographySchemes = themeRepository.typographies.getAll().map { it.filter { !it.builtIn } }
    val shapeSchemes = themeRepository.shapes.getAll().map { it.filter { !it.builtIn } }
    val transparencySchemes = themeRepository.transparencies.getAll().map { it.filter { !it.builtIn } }

    var themeName by mutableStateOf("")
    var themeAuthor by mutableStateOf("")


    fun init() {
        themeName = ""
    }

    var colorScheme by mutableStateOf<Colors?>(null)
        @JvmName("_setColorScheme")
        private set
    fun setColorScheme(scheme: Colors?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        colorScheme = scheme
    }

    var typographyScheme by mutableStateOf<Typography?>(null)
        @JvmName("_setTypographyScheme")
        private set
    fun setTypographyScheme(scheme: Typography?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        typographyScheme = scheme
    }

    var shapeScheme by mutableStateOf<Shapes?>(null)
        @JvmName("_setShapeScheme")
        private set
    fun setShapeScheme(scheme: Shapes?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        shapeScheme = scheme
    }

    var transparencyScheme by mutableStateOf<Transparencies?>(null)
        @JvmName("_setTransparencyScheme")
        private set
    fun setTransparencyScheme(scheme: Transparencies?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        transparencyScheme = scheme
    }

    private fun getThemeBundle(): ThemeBundle {
        return ThemeBundle(
            name = themeName,
            author = themeAuthor.takeIf { it.isNotBlank() },
            colors = colorScheme,
            shapes = shapeScheme,
            typography = typographyScheme,
            transparencies = transparencyScheme,
        )
    }

    fun exportTheme(context: Context, uri: Uri) {
        val themeBundle = getThemeBundle()
        viewModelScope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.writer()?.use {
                it.write(themeBundle.toJson())
            }
        }
    }

    fun shareTheme(context: Context) {
        val themeBundle = getThemeBundle()
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "${themeName}.kvtheme")
                file.writeText(themeBundle.toJson())
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
}