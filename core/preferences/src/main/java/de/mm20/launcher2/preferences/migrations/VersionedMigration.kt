package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.SchemaVersion
import de.mm20.launcher2.preferences.LegacySettings

abstract class VersionedMigration(
    private val fromVersion: Int,
    private val toVersion: Int
) : DataMigration<LegacySettings> {
    abstract suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder

    final override suspend fun migrate(currentData: LegacySettings): LegacySettings {
        val builder = currentData.toBuilder()
        applyMigrations(builder)
        builder.version = toVersion
        return builder.build()
    }

    override suspend fun cleanUp() {}

    override suspend fun shouldMigrate(currentData: LegacySettings): Boolean {
        return currentData.version <= fromVersion && SchemaVersion >= toVersion
    }
}