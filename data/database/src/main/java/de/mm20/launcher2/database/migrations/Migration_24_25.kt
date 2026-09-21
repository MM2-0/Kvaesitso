package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.koin.core.component.KoinComponent

class Migration_24_25 : Migration(24, 25), KoinComponent {

    override suspend fun migrate(connection: SQLiteConnection) {
        // removed
    }
}