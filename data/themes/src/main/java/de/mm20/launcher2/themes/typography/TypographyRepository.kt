package de.mm20.launcher2.themes.typography

import android.content.Context
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.MonospaceId
import de.mm20.launcher2.themes.R
import de.mm20.launcher2.themes.RoundedTypographyId
import de.mm20.launcher2.themes.SerifId
import de.mm20.launcher2.themes.SystemFontId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class TypographyRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun getAll(): Flow<List<Typography>> {
        return database.themeDao().getAllTypographies().map {
            getBuiltIn() + it.map { Typography(it) }
        }
    }

    fun get(id: UUID): Flow<Typography?> {
        if (id == DefaultThemeId) return flowOf(default)
        if (id == RoundedTypographyId) return flowOf(defaultRound)
        if (id == SystemFontId) return flowOf(systemFont)
        if (id == SerifId) return flowOf(serif)
        if (id == MonospaceId) return flowOf(monospace)
        return database.themeDao().getTypography(id).map { it?.let { Typography(it) } }
    }

    fun create(typography: Typography) {
        scope.launch {
            database.themeDao().insertTypography(typography.toEntity())
        }
    }

    fun update(typography: Typography) {
        scope.launch {
            database.themeDao().updateTypography(typography.toEntity())
        }
    }


    fun delete(typography: Typography) {
        scope.launch {
            database.themeDao().deleteTypography(typography.id)
        }
    }

    fun getOrDefault(id: UUID?): Flow<Typography> {
        if (id == null) return flowOf(default)
        return get(id).map { it ?: default }
    }

    private fun getBuiltIn(): List<Typography> {
        return listOf(
            default,
            defaultRound,
            systemFont,
            serif,
            monospace,
        )
    }

    private val default: Typography
        get() = Typography(
            id = DefaultThemeId,
            builtIn = true,
            name = "Google Sans",
            fonts = mapOf(
                "brand" to FontFamily.LauncherDefault,
                "plain" to FontFamily.LauncherDefault,
            ),
            styles = DefaultTextStyles,
            emphasizedStyles = DefaultEmphasizedTextStyles,
        )

    private val defaultRound: Typography
        get() = Typography(
            id = RoundedTypographyId,
            builtIn = true,
            name = "Google Sans (Rounded)",
            fonts = mapOf(
                "brand" to FontFamily.LauncherDefaultRound,
                "plain" to FontFamily.LauncherDefaultRound,
            ),
            styles = DefaultTextStyles,
            emphasizedStyles = DefaultEmphasizedTextStyles,
        )


    private val systemFont: Typography
        get() = Typography(
            id = SystemFontId,
            builtIn = true,
            name = context.getString(R.string.preference_value_system_default),
            fonts = mapOf(
                "brand" to FontFamily.DeviceHeadline,
                "plain" to FontFamily.DeviceBody,
            ),
            styles = DefaultTextStyles,
            emphasizedStyles = DefaultEmphasizedTextStyles,
        )

    private val serif: Typography
        get() = Typography(
            id = SerifId,
            builtIn = true,
            name = "Serif",
            fonts = mapOf(
                "brand" to FontFamily.Serif,
                "plain" to FontFamily.Serif,
            ),
            styles = DefaultTextStyles,
            emphasizedStyles = DefaultEmphasizedTextStyles,
        )

    private val monospace: Typography
        get() = Typography(
            id = MonospaceId,
            builtIn = true,
            name = "Monospace",
            fonts = mapOf(
                "brand" to FontFamily.Monospace,
                "plain" to FontFamily.Monospace,
            ),
            styles = DefaultTextStyles,
            emphasizedStyles = DefaultEmphasizedTextStyles,
        )

}