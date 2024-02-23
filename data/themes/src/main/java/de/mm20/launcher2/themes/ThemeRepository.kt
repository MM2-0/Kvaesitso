package de.mm20.launcher2.themes

import android.content.Context
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.ThemeDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

class ThemeRepository(
    private val context: Context,
    private val database: AppDatabase,
) : Backupable {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun getThemes(): Flow<List<Theme>> {
        return database.themeDao().getAll().map {
            getBuiltInThemes() + it.map { Theme(it) }
        }
    }

    fun getTheme(id: UUID): Flow<Theme?> {
        if (id == DefaultThemeId) return flowOf(getDefaultTheme())
        if (id == BlackAndWhiteThemeId) return flowOf(getBlackAndWhiteTheme())
        return database.themeDao().get(id).map { it?.let { Theme(it) } }.flowOn(Dispatchers.Default)
    }

    fun createTheme(theme: Theme) {
        scope.launch {
            database.themeDao().insert(theme.toEntity())
        }
    }

    fun updateTheme(theme: Theme) {
        scope.launch {
            database.themeDao().update(theme.toEntity())
        }
    }

    fun getThemeOrDefault(theme: ThemeDescriptor?): Flow<Theme> {
        return when(theme) {
            is ThemeDescriptor.BlackAndWhite -> flowOf(getBlackAndWhiteTheme())
            is ThemeDescriptor.Custom -> {
                val id = UUID.fromString(theme.id)
                getTheme(id).map { it ?: getDefaultTheme() }
            }
            else -> flowOf(getDefaultTheme())
        }
    }

    private fun getBuiltInThemes(): List<Theme> {
        return listOf(
            getDefaultTheme(),
            getBlackAndWhiteTheme(),
        )
    }

    fun getDefaultTheme(): Theme {
        return Theme(
            id = DefaultThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_default),
            corePalette = EmptyCorePalette,
            lightColorScheme = DefaultLightColorScheme,
            darkColorScheme = DefaultDarkColorScheme,
        )
    }

    private fun getBlackAndWhiteTheme(): Theme {
        return Theme(
            id = BlackAndWhiteThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_bw),
            corePalette = EmptyCorePalette,
            lightColorScheme = BlackAndWhiteLightColorScheme,
            darkColorScheme = BlackAndWhiteDarkColorScheme,
        )
    }

    fun deleteTheme(theme: Theme) {
        scope.launch {
            database.themeDao().delete(theme.id)
        }
    }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.themeDao()
        val themes = dao.getAll().first().map { Theme(it) }
        val data = ThemeJson.encodeToString(themes)

        val file = File(toDir, "themes.0000")
        file.bufferedWriter().use {
            it.write(data)
        }
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.themeDao()
        dao.deleteAll()

        val files =
            fromDir.listFiles { _, name -> name.startsWith("themes.") }
                ?: return@withContext

        for (file in files) {
            val data = file.inputStream().reader().readText()
            val themes: List<Theme> = try {
                ThemeJson.decodeFromString(data)
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                continue
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                continue
            }
            dao.insertAll(themes.map { it.toEntity() })
        }
    }

}