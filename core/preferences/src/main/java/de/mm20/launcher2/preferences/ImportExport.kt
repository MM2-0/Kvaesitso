package de.mm20.launcher2.preferences

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

internal class LauncherStoreBackupComponent(
    private val context: Context,
    private val dataStore: LegacyDataStore
): Backupable {
    override suspend fun backup(toDir: File) {
        dataStore.export(toDir)
    }

    override suspend fun restore(fromDir: File) {
        dataStore.import(context, fromDir)
    }
}

suspend fun LegacyDataStore.export(toDir: File) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val backupDataStore = DataStoreFactory.create(
        serializer = LegacySettingsSerializer,
        produceFile = {
            File(toDir, "settings")
        },
        scope = scope
    )
    val settings = this.data.firstOrNull() ?: return
    backupDataStore.updateData {
        settings
    }
    scope.cancel()
}


suspend fun LegacyDataStore.import(context: Context, fromDir: File) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val backupDataStore = DataStoreFactory.create(
        serializer = LegacySettingsSerializer,
        migrations = getMigrations(context),
        corruptionHandler = ReplaceFileCorruptionHandler {
            CrashReporter.logException(it)
            LegacySettings.getDefaultInstance()
        },
        produceFile = {
            File(fromDir, "settings")
        },
        scope = scope
    )
    val settings = backupDataStore.data.firstOrNull() ?: return

    this.updateData {
        settings
    }
    scope.cancel()
}