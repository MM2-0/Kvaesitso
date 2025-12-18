package de.mm20.launcher2.themes.transparencies

import android.content.Context
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.R
import de.mm20.launcher2.themes.SemiTransparentId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class TransparenciesRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun getAll(): Flow<List<Transparencies>> {
        return database.themeDao().getAllTransparencies().map {
            getBuiltIn() + it.map { Transparencies(it) }
        }
    }

    fun get(id: UUID): Flow<Transparencies?> {
        if (id == DefaultThemeId) return flowOf(default)
        if (id == SemiTransparentId) return flowOf(semiTransparent)
        return database.themeDao().getTransparencies(id).map { it?.let { Transparencies(it) } }
    }

    fun create(transparencies: Transparencies) {
        scope.launch {
            database.themeDao().insertTransparencies(transparencies.toEntity())
        }
    }

    fun update(transparencies: Transparencies) {
        scope.launch {
            database.themeDao().updateTransparencies(transparencies.toEntity())
        }
    }


    fun delete(transparencies: Transparencies) {
        scope.launch {
            database.themeDao().deleteTransparencies(transparencies.id)
        }
    }

    fun getOrDefault(id: UUID?): Flow<Transparencies> {
        if (id == null) return flowOf(default)
        return get(id).map { it ?: default }
    }

    private fun getBuiltIn(): List<Transparencies> {
        return listOf(
            default,
            semiTransparent,
        )
    }

    private val default: Transparencies
        get() = Transparencies(
            id = DefaultThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_transparencies_default),
            background = 0.85f,
            surface = 1f,
            elevatedSurface = 1f,
        )


    private val semiTransparent: Transparencies
        get() = Transparencies(
            id = SemiTransparentId,
            builtIn = true,
            name = context.getString(R.string.preference_transparencies_semi_transparent),
            background = 0.40f,
            surface = 0.65f,
            elevatedSurface = 0.85f,
        )

}