package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.SchemaVersion
import de.mm20.launcher2.preferences.Settings

abstract class VersionedMigration(
    private val fromVersion: Int,
    private val toVersion: Int
) : DataMigration<Settings> {
    abstract suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder

    final override suspend fun migrate(currentData: Settings): Settings {
        val builder = currentData.toBuilder()
        applyMigrations(builder)
        builder.version = toVersion
        return builder.build()
    }

    override suspend fun cleanUp() {}

    override suspend fun shouldMigrate(currentData: Settings): Boolean {
        return currentData.version <= fromVersion && SchemaVersion >= toVersion
    }
}