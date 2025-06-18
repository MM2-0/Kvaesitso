package de.mm20.launcher2.themes

import android.content.Context
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.themes.colors.ColorsRepository
import de.mm20.launcher2.themes.shapes.ShapesRepository
import de.mm20.launcher2.themes.transparencies.TransparenciesRepository
import de.mm20.launcher2.themes.typography.TypographyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File

class ThemeRepository(
    private val context: Context,
    private val database: AppDatabase,
) : Backupable {
    val colors = ColorsRepository(context, database)
    val shapes = ShapesRepository(context, database)
    val transparencies = TransparenciesRepository(context, database)
    val typographies = TypographyRepository(context, database)


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