package de.mm20.launcher2.themes

import android.content.Context
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.ColorsDescriptor
import de.mm20.launcher2.preferences.ShapesDescriptor
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
import java.io.File
import java.util.UUID

class ThemeRepository(
    private val context: Context,
    private val database: AppDatabase,
) : Backupable {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun getAllColors(): Flow<List<Colors>> {
        return database.themeDao().getAllColors().map {
            getBuiltInColors() + it.map { Colors(it) }
        }
    }

    fun getColors(id: UUID): Flow<Colors?> {
        if (id == DefaultThemeId) return flowOf(getDefaultColors())
        if (id == BlackAndWhiteThemeId) return flowOf(getBlackAndWhiteColors())
        return database.themeDao().getColors(id).map { it?.let { Colors(it) } }.flowOn(Dispatchers.Default)
    }

    fun createColors(colors: Colors) {
        scope.launch {
            database.themeDao().insertColors(colors.toEntity())
        }
    }

    fun updateColors(colors: Colors) {
        scope.launch {
            database.themeDao().updateColors(colors.toEntity())
        }
    }

    fun getColorsOrDefault(theme: ColorsDescriptor?): Flow<Colors> {
        return when(theme) {
            is ColorsDescriptor.BlackAndWhite -> flowOf(getBlackAndWhiteColors())
            is ColorsDescriptor.Custom -> {
                val id = UUID.fromString(theme.id)
                getColors(id).map { it ?: getDefaultColors() }
            }
            else -> flowOf(getDefaultColors())
        }
    }

    private fun getBuiltInColors(): List<Colors> {
        return listOf(
            getDefaultColors(),
            getBlackAndWhiteColors(),
        )
    }

    private fun getDefaultColors(): Colors {
        return Colors(
            id = DefaultThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_default),
            corePalette = EmptyCorePalette,
            lightColorScheme = DefaultLightColorScheme,
            darkColorScheme = DefaultDarkColorScheme,
        )
    }

    private fun getBlackAndWhiteColors(): Colors {
        return Colors(
            id = BlackAndWhiteThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_bw),
            corePalette = EmptyCorePalette,
            lightColorScheme = BlackAndWhiteLightColorScheme,
            darkColorScheme = BlackAndWhiteDarkColorScheme,
        )
    }

    fun deleteColors(colors: Colors) {
        scope.launch {
            database.themeDao().deleteColors(colors.id)
        }
    }

    fun getAllShapes(): Flow<List<Shapes>> {
        return database.themeDao().getAllShapes().map {
            getBuiltInShapes() + it.map { Shapes(it) }
        }
    }

    fun getShapes(id: UUID): Flow<Shapes?> {
        if (id == DefaultThemeId) return flowOf(getDefaultShapes())
        if (id == ExtraRoundShapesId) return flowOf(getExtraRoundShapes())
        if (id == RectShapesId) return flowOf(getRectShapes())
        if (id == CutShapesId) return flowOf(getCutShapes())
        return database.themeDao().getShapes(id).map { it?.let { Shapes(it) } }.flowOn(Dispatchers.Default)
    }

    fun createShapes(shapes: Shapes) {
        scope.launch {
            database.themeDao().insertShapes(shapes.toEntity())
        }
    }

    fun updateShapes(shapes: Shapes) {
        scope.launch {
            database.themeDao().updateShapes(shapes.toEntity())
        }
    }

    fun getShapesOrDefault(theme: ShapesDescriptor?): Flow<Shapes> {
        return when(theme) {
            is ShapesDescriptor.Custom -> {
                val id = UUID.fromString(theme.id)
                getShapes(id).map { it ?: getDefaultShapes() }
            }
            is ShapesDescriptor.ExtraRound -> flowOf(getExtraRoundShapes())
            is ShapesDescriptor.Rect -> flowOf(getRectShapes())
            is ShapesDescriptor.Cut -> flowOf(getCutShapes())
            else -> flowOf(getDefaultShapes())
        }
    }

    private fun getBuiltInShapes(): List<Shapes> {
        return listOf(
            getDefaultShapes(),
            getExtraRoundShapes(),
            getRectShapes(),
            getCutShapes(),
        )
    }

    private fun getDefaultShapes(): Shapes {
        return Shapes(
            id = DefaultThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_shapes_default),
            baseShape = Shape(
                corners = CornerStyle.Rounded,
                radii = intArrayOf(12, 12, 12, 12),
            )
        )
    }

    private fun getCutShapes(): Shapes {
        return Shapes(
            id = CutShapesId,
            builtIn = true,
            name = context.getString(R.string.preference_cards_shape_cut),
            baseShape = Shape(
                corners = CornerStyle.Cut,
                radii = intArrayOf(12, 12, 12, 12),
            )
        )
    }

    private fun getExtraRoundShapes(): Shapes {
        return Shapes(
            id = ExtraRoundShapesId,
            builtIn = true,
            name = context.getString(R.string.preference_shapes_extra_round),
            baseShape = Shape(
                corners = CornerStyle.Rounded,
                radii = intArrayOf(24, 24, 24, 24),
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
    }

    private fun getRectShapes(): Shapes {
        return Shapes(
            id = RectShapesId,
            builtIn = true,
            name = context.getString(R.string.preference_shapes_rect),
            baseShape = Shape(
                corners = CornerStyle.Rounded,
                radii = intArrayOf(0, 0, 0, 0),
            )
        )
    }

    fun deleteShapes(shapes: Shapes) {
        scope.launch {
            database.themeDao().deleteShapes(shapes.id)
        }
    }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.themeDao()
        val colors = dao.getAllColors().first().map { Colors(it) }
        val data = LegacyThemeJson.encodeToString(colors)

        val file = File(toDir, "themes.0000")
        file.bufferedWriter().use {
            it.write(data)
        }
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.themeDao()
        dao.deleteAllColors()

        val files =
            fromDir.listFiles { _, name -> name.startsWith("themes.") }
                ?: return@withContext

        for (file in files) {
            val data = file.inputStream().reader().readText()
            val colors: List<Colors> = try {
                LegacyThemeJson.decodeFromString(data)
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                continue
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                continue
            }
            dao.insertAllColors(colors.map { it.toEntity() })
        }
    }

}