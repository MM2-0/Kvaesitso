package de.mm20.launcher2.database.migrations

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.database.R
import de.mm20.launcher2.ktx.toBytes
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class Migration_24_25(
    private val context: Context,
) : Migration(24, 25), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Theme` (
                `id` BLOB NOT NULL,
                `name` TEXT NOT NULL,
                
                `corePaletteA1` INTEGER,
                `corePaletteA2` INTEGER,
                `corePaletteA3` INTEGER,
                `corePaletteN1` INTEGER,
                `corePaletteN2` INTEGER,
                `corePaletteE` INTEGER,
                `lightPrimary` TEXT,
                `lightOnPrimary` TEXT,
                `lightPrimaryContainer` TEXT,
                `lightOnPrimaryContainer` TEXT,
                `lightSecondary` TEXT,
                `lightOnSecondary` TEXT,
                `lightSecondaryContainer` TEXT,
                `lightOnSecondaryContainer` TEXT,
                `lightTertiary` TEXT,
                `lightOnTertiary` TEXT,
                `lightTertiaryContainer` TEXT,
                `lightOnTertiaryContainer` TEXT,
                `lightError` TEXT,
                `lightOnError` TEXT,
                `lightErrorContainer` TEXT,
                `lightOnErrorContainer` TEXT,
                `lightSurface` TEXT,
                `lightOnSurface` TEXT,
                `lightOnSurfaceVariant` TEXT,
                `lightOutline` TEXT,
                `lightOutlineVariant` TEXT,
                `lightInverseSurface` TEXT,
                `lightInverseOnSurface` TEXT,
                `lightInversePrimary` TEXT,
                `lightSurfaceDim` TEXT,
                `lightSurfaceBright` TEXT,
                `lightSurfaceContainerLowest` TEXT,
                `lightSurfaceContainerLow` TEXT,
                `lightSurfaceContainer` TEXT,
                `lightSurfaceContainerHigh` TEXT,
                `lightSurfaceContainerHighest` TEXT,
                `lightBackground` TEXT,
                `lightOnBackground` TEXT,
                `lightSurfaceTint` TEXT,
                `lightScrim` TEXT,
                `lightSurfaceVariant` TEXT,
            
                `darkPrimary` TEXT,
                `darkOnPrimary` TEXT,
                `darkPrimaryContainer` TEXT,
                `darkOnPrimaryContainer` TEXT,
                `darkSecondary` TEXT,
                `darkOnSecondary` TEXT,
                `darkSecondaryContainer` TEXT,
                `darkOnSecondaryContainer` TEXT,
                `darkTertiary` TEXT,
                `darkOnTertiary` TEXT,
                `darkTertiaryContainer` TEXT,
                `darkOnTertiaryContainer` TEXT,
                `darkError` TEXT,
                `darkOnError` TEXT,
                `darkErrorContainer` TEXT,
                `darkOnErrorContainer` TEXT,
                `darkSurface` TEXT,
                `darkOnSurface` TEXT,
                `darkOnSurfaceVariant` TEXT,
                `darkOutline` TEXT,
                `darkOutlineVariant` TEXT,
                `darkInverseSurface` TEXT,
                `darkInverseOnSurface` TEXT,
                `darkInversePrimary` TEXT,
                `darkSurfaceDim` TEXT,
                `darkSurfaceBright` TEXT,
                `darkSurfaceContainerLowest` TEXT,
                `darkSurfaceContainerLow` TEXT,
                `darkSurfaceContainer` TEXT,
                `darkSurfaceContainerHigh` TEXT,
                `darkSurfaceContainerHighest` TEXT,
                `darkBackground` TEXT,
                `darkOnBackground` TEXT,
                `darkSurfaceTint` TEXT,
                `darkScrim` TEXT,
                `darkSurfaceVariant` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        // Special UUID for migrated custom color scheme. Same UUID is used in data store migration 16..17
        val uuid = UUID(1L, 1L)
        val customColors = runBlocking {
            dataStore.data.map { it.appearance.customColors }.first()
        }

        database.execSQL("""INSERT INTO `Theme` VALUES (
            ?,?,?,?,?,?,?,?,?,?,
            ?,?,?,?,?,?,?,?,?,?,
            ?,?,?,?,?,?,?,?,?,?,
            ?,?,?,?,?,?,?,?,?,?,
            ?,?,?,?,?,?,?,?,?,?,
            ?,?,?,?,?,?,?,?,?,?,
            ?,?,?,?,?,?,?,?,?,?,
            ?,?,?,?,?,?,?,?,?,?
        )
        """.trimIndent(),
            arrayOf(
                uuid.toBytes(),
                context.getString(R.string.preference_colors_custom),
                customColors.baseColors.accent1.toHexColor(),
                customColors.baseColors.accent2.toHexColor(),
                customColors.baseColors.accent3.toHexColor(),
                customColors.baseColors.neutral1.toHexColor(),
                customColors.baseColors.neutral2.toHexColor(),
                customColors.baseColors.error.toHexColor(),
                customColors.lightScheme.primary.toHexColor(),
                customColors.lightScheme.onPrimary.toHexColor(),
                customColors.lightScheme.primaryContainer.toHexColor(),
                customColors.lightScheme.onPrimaryContainer.toHexColor(),
                customColors.lightScheme.secondary.toHexColor(),
                customColors.lightScheme.onSecondary.toHexColor(),
                customColors.lightScheme.secondaryContainer.toHexColor(),
                customColors.lightScheme.onSecondaryContainer.toHexColor(),
                customColors.lightScheme.tertiary.toHexColor(),
                customColors.lightScheme.onTertiary.toHexColor(),
                customColors.lightScheme.tertiaryContainer.toHexColor(),
                customColors.lightScheme.onTertiaryContainer.toHexColor(),
                customColors.lightScheme.error.toHexColor(),
                customColors.lightScheme.onError.toHexColor(),
                customColors.lightScheme.errorContainer.toHexColor(),
                customColors.lightScheme.onErrorContainer.toHexColor(),
                customColors.lightScheme.surface.toHexColor(),
                customColors.lightScheme.onSurface.toHexColor(),
                customColors.lightScheme.onSurfaceVariant.toHexColor(),
                customColors.lightScheme.outline.toHexColor(),
                customColors.lightScheme.outlineVariant.toHexColor(),
                customColors.lightScheme.inverseSurface.toHexColor(),
                customColors.lightScheme.inverseOnSurface.toHexColor(),
                customColors.lightScheme.inversePrimary.toHexColor(),
                customColors.lightScheme.surfaceDim.toHexColor(),
                customColors.lightScheme.surfaceBright.toHexColor(),
                customColors.lightScheme.surfaceContainerLowest.toHexColor(),
                customColors.lightScheme.surfaceContainerLow.toHexColor(),
                customColors.lightScheme.surfaceContainer.toHexColor(),
                customColors.lightScheme.surfaceContainerHigh.toHexColor(),
                customColors.lightScheme.surfaceContainerHighest.toHexColor(),
                customColors.lightScheme.background.toHexColor(),
                customColors.lightScheme.onBackground.toHexColor(),
                customColors.lightScheme.surfaceTint.toHexColor(),
                customColors.lightScheme.scrim.toHexColor(),
                customColors.lightScheme.surfaceVariant.toHexColor(),

                customColors.darkScheme.primary.toHexColor(),
                customColors.darkScheme.onPrimary.toHexColor(),
                customColors.darkScheme.primaryContainer.toHexColor(),
                customColors.darkScheme.onPrimaryContainer.toHexColor(),
                customColors.darkScheme.secondary.toHexColor(),
                customColors.darkScheme.onSecondary.toHexColor(),
                customColors.darkScheme.secondaryContainer.toHexColor(),
                customColors.darkScheme.onSecondaryContainer.toHexColor(),
                customColors.darkScheme.tertiary.toHexColor(),
                customColors.darkScheme.onTertiary.toHexColor(),
                customColors.darkScheme.tertiaryContainer.toHexColor(),
                customColors.darkScheme.onTertiaryContainer.toHexColor(),
                customColors.darkScheme.error.toHexColor(),
                customColors.darkScheme.onError.toHexColor(),
                customColors.darkScheme.errorContainer.toHexColor(),
                customColors.darkScheme.onErrorContainer.toHexColor(),
                customColors.darkScheme.surface.toHexColor(),
                customColors.darkScheme.onSurface.toHexColor(),
                customColors.darkScheme.onSurfaceVariant.toHexColor(),
                customColors.darkScheme.outline.toHexColor(),
                customColors.darkScheme.outlineVariant.toHexColor(),
                customColors.darkScheme.inverseSurface.toHexColor(),
                customColors.darkScheme.inverseOnSurface.toHexColor(),
                customColors.darkScheme.inversePrimary.toHexColor(),
                customColors.darkScheme.surfaceDim.toHexColor(),
                customColors.darkScheme.surfaceBright.toHexColor(),
                customColors.darkScheme.surfaceContainerLowest.toHexColor(),
                customColors.darkScheme.surfaceContainerLow.toHexColor(),
                customColors.darkScheme.surfaceContainer.toHexColor(),
                customColors.darkScheme.surfaceContainerHigh.toHexColor(),
                customColors.darkScheme.surfaceContainerHighest.toHexColor(),
                customColors.darkScheme.background.toHexColor(),
                customColors.darkScheme.onBackground.toHexColor(),
                customColors.darkScheme.surfaceTint.toHexColor(),
                customColors.darkScheme.scrim.toHexColor(),
                customColors.darkScheme.surfaceVariant.toHexColor(),
            )
        )
    }

    fun Int.toHexColor(): String {
        return "#${toUInt().toString(16).padStart(6, '0')}"
    }
}