package de.mm20.launcher2.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import de.mm20.launcher2.backup.Backupable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.File

abstract class BaseSettings<T>(
    internal val context: Context,
    private val fileName: String,
    private val serializer: Serializer<T>,
    migrations: List<DataMigration<T>>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>? = null,
): Backupable {

    protected val scope = CoroutineScope(Job() + Dispatchers.Default)

    protected val Context.dataStore by dataStore(
        fileName = fileName,
        serializer = serializer,
        produceMigrations = {
            migrations
        },
        corruptionHandler = corruptionHandler
    )

    protected fun updateData(block: suspend (T) -> T) {
        scope.launch {
            context.dataStore.updateData(block)
        }
    }

    override suspend fun backup(toDir: File) {
        val data = context.dataStore.data.first()
        val file = File(toDir, fileName)
        file.outputStream().use {
            serializer.writeTo(data, it)
        }
    }

    override suspend fun restore(fromDir: File) {
        val file = File(fromDir, fileName)
        if (!file.exists()) {
            return
        }
        try {
            file.inputStream().use {
                val data = serializer.readFrom(it)
                context.dataStore.updateData {
                    data
                }
            }
        } catch (e: SerializationException) {
            Log.e("MM20", "Cannot restore $fileName", e)
        } catch (e: IllegalArgumentException) {
            Log.e("MM20", "Cannot restore $fileName", e)
        }
    }
}