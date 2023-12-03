package de.mm20.launcher2.files.settings

import android.content.Context
import androidx.datastore.dataStore
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.files.settings.migrations.Migration1
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

class FileSearchSettings(
    private val context: Context,
    private val dataStore: LauncherDataStore,
) : Backupable {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val Context.dataStore by dataStore(
        fileName = "file_search.json",
        serializer = FileSearchSettingsDataSerializer,
        produceMigrations = {
            listOf(
                Migration1(dataStore),
            )
        },
    )

    private fun updateData(block: suspend (FileSearchSettingsData) -> FileSearchSettingsData) {
        scope.launch {
            context.dataStore.updateData(block)
        }
    }

    internal val data
        get() = context.dataStore.data

    val localFiles
        get(): Flow<Boolean> {
            return context.dataStore.data.map { it.localFiles }
        }

    fun setLocalFiles(localFiles: Boolean) {
        updateData {
            it.copy(localFiles = localFiles)
        }
    }

    val gdriveFiles
        get(): Flow<Boolean> {
            return context.dataStore.data.map { it.gdriveFiles }
        }

    fun setGdriveFiles(gdriveFiles: Boolean) {
        updateData {
            it.copy(gdriveFiles = gdriveFiles)
        }
    }

    val nextcloudFiles
        get(): Flow<Boolean> {
            return context.dataStore.data.map { it.nextcloudFiles }
        }

    fun setNextcloudFiles(nextcloudFiles: Boolean) {
        updateData {
            it.copy(nextcloudFiles = nextcloudFiles)
        }
    }

    val owncloudFiles
        get(): Flow<Boolean> {
            return context.dataStore.data.map { it.owncloudFiles }
        }

    fun setOwncloudFiles(owncloudFiles: Boolean) {
        updateData {
            it.copy(owncloudFiles = owncloudFiles)
        }
    }

    val enabledPlugins: Flow<Set<String>>
        get(): Flow<Set<String>> {
            return context.dataStore.data.map { it.plugins }
        }

    fun setEnabledPlugins(enabledPlugins: Set<String>) {
        updateData {
            it.copy(plugins = enabledPlugins)
        }
    }

    override suspend fun backup(toDir: File) {
        val data = context.dataStore.data.first()
        val file = File(toDir, "file_search.json")
        file.writeText(FileSearchSettingsDataSerializer.json.encodeToString(FileSearchSettingsData.serializer(), data))
    }

    override suspend fun restore(fromDir: File) {
        val file = File(fromDir, "file_search.json")
        if (!file.exists()) {
            return
        }
        try {
            val data = FileSearchSettingsDataSerializer.json.decodeFromString(FileSearchSettingsData.serializer(), file.readText())
            context.dataStore.updateData {
                data
            }
        } catch (e: SerializationException) {
            CrashReporter.logException(e)
        } catch (e: IllegalArgumentException) {
            CrashReporter.logException(e)
        }
    }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        updateData {
            if (enabled) {
                it.copy(plugins = it.plugins + authority)
            } else {
                it.copy(plugins = it.plugins - authority)
            }
        }
    }
}