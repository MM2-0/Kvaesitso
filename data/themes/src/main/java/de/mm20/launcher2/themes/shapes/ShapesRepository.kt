package de.mm20.launcher2.themes.shapes

import android.content.Context
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.themes.CutShapesId
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.ExtraRoundShapesId
import de.mm20.launcher2.themes.R
import de.mm20.launcher2.themes.RectShapesId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class ShapesRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun getAll(): Flow<List<Shapes>> {
        return database.themeDao().getAllShapes().map {
            getBuiltIn() + it.map { Shapes(it) }
        }
    }

    fun get(id: UUID): Flow<Shapes?> {
        if (id == DefaultThemeId) return flowOf(default)
        if (id == ExtraRoundShapesId) return flowOf(extraRound)
        if (id == RectShapesId) return flowOf(rect)
        if (id == CutShapesId) return flowOf(cut)
        return database.themeDao().getShapes(id).map { it?.let { Shapes(it) } }
            .flowOn(Dispatchers.Default)
    }

    fun create(shapes: Shapes) {
        scope.launch {
            database.themeDao().insertShapes(shapes.toEntity())
        }
    }

    fun update(shapes: Shapes) {
        scope.launch {
            database.themeDao().updateShapes(shapes.toEntity())
        }
    }

    fun delete(shapes: Shapes) {
        scope.launch {
            database.themeDao().deleteShapes(shapes.id)
        }
    }

    fun getOrDefault(id: UUID?): Flow<Shapes> {
        if (id == null) return flowOf(default)
        return get(id).map { it ?: default }
    }

    private fun getBuiltIn(): List<Shapes> {
        return listOf(
            default,
            extraRound,
            rect,
            cut,
        )
    }

    private val default: Shapes
        get() = Shapes(
            id = DefaultThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_shapes_default),
            baseShape = Shape(
                corners = CornerStyle.Rounded,
                radii = intArrayOf(12, 12, 12, 12),
            )
        )

    private val cut: Shapes
        get() = Shapes(
            id = CutShapesId,
            builtIn = true,
            name = context.getString(R.string.preference_cards_shape_cut),
            baseShape = Shape(
                corners = CornerStyle.Cut,
                radii = intArrayOf(12, 12, 12, 12),
            )
        )

    private val extraRound: Shapes
        get() = Shapes(
            id = ExtraRoundShapesId,
            builtIn = true,
            name = context.getString(R.string.preference_shapes_extra_round),
            baseShape = Shape(
                corners = CornerStyle.Rounded,
                radii = intArrayOf(24, 24, 24, 24),
            ),
            extraSmall = Shape(
                radii = intArrayOf(4, 4, 4, 4),
            ),
            extraLarge = Shape(
                radii = intArrayOf(36, 36, 36, 36),
            ),
            extraLargeIncreased = Shape(
                radii = intArrayOf(40, 40, 40, 40),
            ),
            extraExtraLarge = Shape(
                radii = intArrayOf(56, 56, 56, 56),
            )
        )


    private val rect: Shapes
        get() = Shapes(
            id = RectShapesId,
            builtIn = true,
            name = context.getString(R.string.preference_shapes_rect),
            baseShape = Shape(
                corners = CornerStyle.Rounded,
                radii = intArrayOf(0, 0, 0, 0),
            )
        )
}
