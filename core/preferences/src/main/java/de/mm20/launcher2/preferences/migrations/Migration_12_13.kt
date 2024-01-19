package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings
import de.mm20.launcher2.preferences.LegacySettings.SearchResultOrderingSettings.WeightFactor

class Migration_12_13: VersionedMigration(12, 13) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder
            .setClockWidget(
                builder.clockWidget.toBuilder()
                    .setDatePart(true)
                    .build()
            )
            .setResultOrdering(
                builder.resultOrdering.toBuilder()
                    .setWeightFactor(WeightFactor.Default)
                    .build()
            )
    }
}