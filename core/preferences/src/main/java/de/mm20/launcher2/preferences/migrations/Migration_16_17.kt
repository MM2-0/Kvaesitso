package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings
import java.util.UUID

class Migration_16_17: VersionedMigration(16, 17) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setAppearance(
            builder.appearance.toBuilder()
                .setThemeId(
                    when(builder.appearance.colorScheme) {
                        Settings.AppearanceSettings.ColorScheme.BlackAndWhite -> UUID(0L, 1L)
                        Settings.AppearanceSettings.ColorScheme.Custom -> UUID(1L, 1L)
                        else -> UUID(0L, 0L)
                    }.toString()
                )
        )
    }
}