package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.CustomColors.Scheme
import palettes.TonalPalette

class Migration_15_16 : VersionedMigration(15, 16) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setAppearance(
            builder.appearance.toBuilder()
                .setCustomColors(
                    builder.appearance.customColors.toBuilder()
                        .setLightScheme(
                            migrateColorScheme(
                                builder.appearance.customColors.lightScheme,
                                false,
                                builder.appearance.customColors.advancedMode
                            )
                        )
                        .setDarkScheme(
                            migrateColorScheme(
                                builder.appearance.customColors.darkScheme,
                                true,
                                builder.appearance.customColors.advancedMode
                            )
                        )
                )
        )
    }

    fun migrateColorScheme(colorScheme: Scheme, dark: Boolean, advancedMode: Boolean): Scheme {
        val oldSurface = colorScheme.surface
        val oldSurfaceVariant = colorScheme.surfaceVariant
        val neutralPalette = TonalPalette.fromInt(oldSurface)
        val neutralVariantPalette = TonalPalette.fromInt(oldSurfaceVariant)

        return colorScheme.toBuilder().apply {
            if (!advancedMode) {
                surface = neutralPalette.tone(if (dark) 6 else 98)
            }
            surfaceDim = neutralPalette.tone(if (dark) 6 else 87)
            surfaceBright = neutralPalette.tone(if (dark) 24 else 98)
            surfaceContainerLowest = neutralVariantPalette.tone(if (dark) 4 else 100)
            surfaceContainerLow = neutralVariantPalette.tone(if (dark) 10 else 96)
            surfaceContainer = neutralVariantPalette.tone(if (dark) 12 else 94)
            surfaceContainerHigh = neutralVariantPalette.tone(if (dark) 17 else 92)
            surfaceContainerHighest = neutralVariantPalette.tone(if (dark) 22 else 90)
        }.build()
    }

}