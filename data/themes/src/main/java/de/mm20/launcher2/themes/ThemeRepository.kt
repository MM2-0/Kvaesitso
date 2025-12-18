package de.mm20.launcher2.themes

import android.content.Context
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.ColorsEntity
import de.mm20.launcher2.database.entities.ShapesEntity
import de.mm20.launcher2.database.entities.TransparenciesEntity
import de.mm20.launcher2.database.entities.TypographyEntity
import de.mm20.launcher2.serialization.Json
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

        val colors = dao.getAllColors().first()
        val colorsFile = File(toDir, "colors.0000")
        colorsFile.bufferedWriter().use {
            it.write(Json.Lenient.encodeToString(colors))
        }

        val shapes = dao.getAllShapes().first()
        val shapesFile = File(toDir, "shapes.0000")
        shapesFile.bufferedWriter().use {
            it.write(Json.Lenient.encodeToString(shapes))
        }

        val transparencies = dao.getAllTransparencies().first()
        val transparenciesFile = File(toDir, "transparencies.0000")
        transparenciesFile.bufferedWriter().use {
            it.write(Json.Lenient.encodeToString(transparencies))
        }

        val typographies = dao.getAllTypographies().first()
        val typographiesFile = File(toDir, "typographies.0000")
        typographiesFile.bufferedWriter().use {
            it.write(Json.Lenient.encodeToString(typographies))
        }
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.themeDao()
        dao.deleteAllColors()

        val colorFiles =
            fromDir.listFiles { _, name -> name.startsWith("colors.") }
                ?: return@withContext

        for (file in colorFiles) {
            val data = file.inputStream().reader().readText()
            val colors: List<ColorsEntity> = try {
                Json.Lenient.decodeFromString(data)
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                continue
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                continue
            }
            dao.insertAllColors(colors)
        }

        dao.deleteAllShapes()
        val shapeFiles =
            fromDir.listFiles { _, name -> name.startsWith("shapes.") }
                ?: return@withContext
        for (file in shapeFiles) {
            val data = file.inputStream().reader().readText()
            val shapes: List<ShapesEntity> = try {
                Json.Lenient.decodeFromString(data)
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                continue
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                continue
            }
            dao.insertAllShapes(shapes)
        }

        dao.deleteAllTransparencies()
        val transparencyFiles =
            fromDir.listFiles { _, name -> name.startsWith("transparencies.") }
                ?: return@withContext
        for (file in transparencyFiles) {
            val data = file.inputStream().reader().readText()
            val transparencies: List<TransparenciesEntity>  = try {
                Json.Lenient.decodeFromString(data)
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                continue
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                continue
            }
            dao.insertAllTransparencies(transparencies)
        }

        dao.deleteAllTypographies()
        val typographyFiles =
            fromDir.listFiles { _, name -> name.startsWith("typographies.") }
                ?: return@withContext
        for (file in typographyFiles) {
            val data = file.inputStream().reader().readText()
            val typographies: List<TypographyEntity> = try {
                Json.Lenient.decodeFromString(data)
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                continue
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                continue
            }
            dao.insertAllTypographies(typographies)
        }


    }
}