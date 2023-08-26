package de.mm20.launcher2.ui.settings.colorscheme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.ByteString
import de.mm20.launcher2.ktx.toBytes
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.Theme
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.fromJson
import de.mm20.launcher2.themes.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
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

    fun duplicate(theme: Theme) {
        themeRepository.createTheme(theme.copy(id = UUID.randomUUID()))
    }

    fun delete(theme: Theme) {
        themeRepository.deleteTheme(theme)
    }

    fun exportTheme(context: Context, theme: Theme) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "${theme.name}.kvtheme")
                file.writeText(theme.toJson())
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

    fun importTheme(context: Context, uri: Uri?) {
        uri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use {
                val theme = Theme.fromJson(it.readBytes().toString(Charsets.UTF_8))
                themeRepository.createTheme(theme.copy(id = UUID.randomUUID()))
            }
        }
    }
}