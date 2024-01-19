package de.mm20.launcher2.ui.common

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ThemeDescriptor
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.Theme
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.fromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ImportThemeSheetVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()

    val theme = mutableStateOf<Theme?>(null)
    val error = mutableStateOf<Boolean>(false)
    val apply = mutableStateOf<Boolean>(false)

    fun import() {
        val theme = theme.value
        val apply = apply.value
        if (theme != null) {
            viewModelScope.launch {
                importTheme(theme, apply)
            }
        }
    }


    fun readTheme(context: Context, uri: Uri) {
        error.value = false
        theme.value = null
        apply.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val inputStream =
                context.contentResolver.openInputStream(uri) ?: return@launch
            val theme = inputStream.use {
                val json = it.readBytes().toString(Charsets.UTF_8)
                try {
                    Theme.fromJson(json)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            this@ImportThemeSheetVM.theme.value = theme
            if (theme == null) {
                error.value = true
            }
        }
    }

    private fun importTheme(theme: Theme, apply: Boolean) {
        themeRepository.createTheme(theme)
        if (apply) {
            uiSettings.setTheme(ThemeDescriptor.Custom(theme.id.toString()))
        }
    }
}