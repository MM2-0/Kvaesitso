package de.mm20.launcher2.files

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.files.providers.DeferredFile
import de.mm20.launcher2.files.providers.GDriveFile
import de.mm20.launcher2.files.providers.LocalFile
import de.mm20.launcher2.files.providers.NextcloudFile
import de.mm20.launcher2.files.providers.OneDriveFile
import de.mm20.launcher2.files.providers.OwncloudFile
import de.mm20.launcher2.files.providers.PluginFile
import de.mm20.launcher2.files.providers.PluginFileProvider
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

internal class LocalFileSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as LocalFile
        return jsonObjectOf(
            "id" to searchable.id
        ).toString()
    }

    override val typePrefix: String
        get() = "file"
}

internal class LocalFileDeserializer(
    val context: Context
) : SearchableDeserializer, KoinComponent {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val permissionsManager: PermissionsManager = get()
        if (!permissionsManager.checkPermissionOnce(
                PermissionGroup.ExternalStorage
            )
        ) return null
        val json = JSONObject(serialized)
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val sel = "${MediaStore.Files.FileColumns._ID} = ?"
        val selArgs = arrayOf(json.getLong("id").toString())
        val cursor = context.contentResolver.query(uri, proj, sel, selArgs, null) ?: return null
        if (cursor.moveToNext()) {
            val path = cursor.getString(2)
            if (!java.io.File(path).exists()) return null
            val directory = java.io.File(path).isDirectory
            val id = cursor.getLong(0)
            val mimeType = cursor.getStringOrNull(3).takeIf { it != "application/octet-stream" }
                ?: if (directory) "resource/folder" else LocalFile.getMimetypeByFileExtension(
                    path.substringAfterLast(
                        '.'
                    )
                )
            val size = cursor.getLong(1)
            cursor.close()
            return LocalFile(
                path = path,
                mimeType = mimeType,
                size = size,
                isDirectory = directory,
                id = id,
                metaData = LocalFile.getMetaData(context, mimeType, path)
            )
        }
        cursor.close()
        return null
    }
}

internal class GDriveFileSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as GDriveFile
        return jsonObjectOf(
            "id" to searchable.fileId,
            "label" to searchable.label,
            "path" to searchable.path,
            "mimeType" to searchable.mimeType,
            "size" to searchable.size,
            "directory" to searchable.isDirectory,
            "color" to searchable.directoryColor,
            "uri" to searchable.viewUri
        ).apply {
            for ((k, v) in searchable.metaData) {
                put(
                    when (k) {
                        FileMetaType.Owner -> "owner"
                        FileMetaType.Dimensions -> "dimensions"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "gdrive"
}

internal class GDriveFileDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        val id = json.getString("id")
        val label = json.getString("label")
        val path = json.getString("path")
        val mimeType = json.getString("mimeType")
        val size = json.getLong("size")
        val directory = json.getBoolean("directory")
        val color = json.optString("color")
        val uri = json.getString("uri")
        val owner = json.optString("owner")
        val dimensions = json.optString("dimensions")
        val metaData = mutableMapOf<FileMetaType, String>()
        owner.takeIf { it.isNotEmpty() }?.let { metaData[FileMetaType.Owner] = it }
        dimensions.takeIf { it.isNotEmpty() }
            ?.let { metaData[FileMetaType.Dimensions] = it }
        return GDriveFile(
            fileId = id,
            label = label,
            path = path,
            mimeType = mimeType,
            size = size,
            directoryColor = color,
            isDirectory = directory,
            viewUri = uri,
            metaData = metaData.toImmutableMap()
        )
    }
}

internal class OneDriveFileSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OneDriveFile
        return jsonObjectOf(
            "id" to searchable.fileId,
            "label" to searchable.label,
            "mimeType" to searchable.mimeType,
            "size" to searchable.size,
            "directory" to searchable.isDirectory,
            "webUrl" to searchable.webUrl
        ).apply {
            for ((k, v) in searchable.metaData) {
                put(
                    when (k) {
                        FileMetaType.Owner -> "owner"
                        FileMetaType.Dimensions -> "dimensions"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "onedrive"
}

internal class OneDriveFileDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        val fileId = json.getString("id")
        val label = json.getString("label")
        val mimeType = json.getString("mimeType")
        val size = json.getLong("size")
        val isDirectory = json.getBoolean("directory")
        val webUrl = json.getString("webUrl")
        val owner = json.optString("owner")
        val dimensions = json.optString("dimensions")
        val metaData = mutableMapOf<FileMetaType, String>()
        owner.takeIf { it.isNotEmpty() }?.let { metaData[FileMetaType.Owner] = it }
        dimensions.takeIf { it.isNotEmpty() }
            ?.let { metaData[FileMetaType.Dimensions] = it }
        return OneDriveFile(
            fileId = fileId,
            label = label,
            path = "",
            mimeType = mimeType,
            size = size,
            isDirectory = isDirectory,
            metaData = metaData.toImmutableMap(),
            webUrl = webUrl
        )
    }
}

internal class NextcloudFileSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as NextcloudFile
        return jsonObjectOf(
            "id" to searchable.fileId,
            "label" to searchable.label,
            "path" to searchable.path,
            "mimeType" to searchable.mimeType,
            "size" to searchable.size,
            "isDirectory" to searchable.isDirectory,
            "server" to searchable.server
        ).apply {
            for ((k, v) in searchable.metaData) {
                put(
                    when (k) {
                        FileMetaType.Owner -> "owner"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "nextcloud"
}

internal class NextcloudFileDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        val id = json.getLong("id")
        val label = json.getString("label")
        val path = json.getString("path")
        val mimeType = json.getString("mimeType")
        val size = json.getLong("size")
        val isDirectory = json.getBoolean("isDirectory")
        val server = json.getString("server")
        val owner = json.optString("owner").takeIf { it.isNotEmpty() }

        return NextcloudFile(
            fileId = id,
            label = label,
            path = path,
            mimeType = mimeType,
            size = size,
            isDirectory = isDirectory,
            server = server,
            metaData = owner?.let { persistentMapOf(FileMetaType.Owner to it) } ?: persistentMapOf()

        )
    }
}

internal class OwncloudFileSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OwncloudFile
        return jsonObjectOf(
            "id" to searchable.fileId,
            "label" to searchable.label,
            "path" to searchable.path,
            "mimeType" to searchable.mimeType,
            "size" to searchable.size,
            "isDirectory" to searchable.isDirectory,
            "server" to searchable.server
        ).apply {
            for ((k, v) in searchable.metaData) {
                put(
                    when (k) {
                        FileMetaType.Owner -> "owner"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "owncloud"
}

internal class OwncloudFileDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        val id = json.getLong("id")
        val label = json.getString("label")
        val path = json.getString("path")
        val mimeType = json.getString("mimeType")
        val size = json.getLong("size")
        val isDirectory = json.getBoolean("isDirectory")
        val server = json.getString("server")
        val owner = json.optString("owner").takeIf { it.isNotEmpty() }

        return OwncloudFile(
            fileId = id,
            label = label,
            path = path,
            mimeType = mimeType,
            size = size,
            isDirectory = isDirectory,
            server = server,
            metaData = owner?.let { persistentMapOf(FileMetaType.Owner to it) } ?: persistentMapOf()

        )
    }
}

internal class PluginFileSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String? {
        searchable as PluginFile
        if (searchable.storageStrategy == StorageStrategy.StoreReference) {
            return jsonObjectOf(
                "id" to searchable.id,
                "authority" to searchable.authority,
                "strategy" to "ref"
            ).toString()
        } else {
            return jsonObjectOf(
                "id" to searchable.id,
                "path" to searchable.path,
                "mimeType" to searchable.mimeType,
                "size" to searchable.size,
                "label" to searchable.label,
                "uri" to searchable.uri.toString(),
                "thumbnailUri" to searchable.thumbnailUri?.toString(),
                "isDirectory" to searchable.isDirectory,
                "authority" to searchable.authority,
                "strategy" to if (searchable.storageStrategy == StorageStrategy.StoreCopy) "copy" else "deferred",
            ).toString()
        }
    }

    override val typePrefix: String
        get() = PluginFile.Domain

}

internal class PluginFileDeserializer(
    private val context: Context,
    private val pluginRepository: PluginRepository,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val jsonObject = JSONObject(serialized)

        return when (jsonObject.optString("strategy", "copy")) {
            "ref" -> {
                getByRef(jsonObject)
            }

            "deferred" -> {
                getDeferred(jsonObject)
            }

            else -> {
                getByCopy(jsonObject)
            }
        }
    }

    private suspend fun getByRef(obj: JSONObject): File? {
        try {
            val authority = obj.getString("authority")
            val id = obj.getString("id")
            val plugin = pluginRepository.get(authority).firstOrNull() ?: return null
            if (!plugin.enabled) return null
            val provider = PluginFileProvider(context, authority)
            return provider.getFile(id)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }
    }

    private fun getDeferred(obj: JSONObject): File? {
        val cached = getByCopy(obj) ?: return null
        val timestamp = obj.optLong("timestamp", 0L)
        return DeferredFile(
            cachedFile = cached as PluginFile,
            timestamp = timestamp,
            updatedSelf = {
                val plugin = pluginRepository.get(cached.authority).firstOrNull()
                    ?: return@DeferredFile UpdateResult.PermanentlyUnavailable()
                if (!plugin.enabled) return@DeferredFile UpdateResult.PermanentlyUnavailable()
                val provider = PluginFileProvider(context, cached.authority)
                try {
                    val file = provider.getFile(cached.id)
                    if (file == null) {
                        UpdateResult.PermanentlyUnavailable()
                    } else {
                        UpdateResult.Success(file)
                    }
                } catch (e: Exception) {
                    CrashReporter.logException(e)
                    UpdateResult.TemporarilyUnavailable(e)
                }
            }
        )
    }

    private fun getByCopy(obj: JSONObject): File? {
        try {
            val uri = obj.getString("uri")
            val thumbnailUri = obj.optString("thumbnailUri")
            return PluginFile(
                id = obj.getString("id"),
                path = obj.getString("path"),
                mimeType = obj.getString("mimeType"),
                size = obj.optLong("size", 0L),
                metaData = persistentMapOf(),
                label = obj.getString("label"),
                uri = Uri.parse(uri),
                thumbnailUri = thumbnailUri.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                storageStrategy = StorageStrategy.StoreCopy,
                isDirectory = obj.optBoolean("isDirectory", false),
                authority = obj.getString("authority"),
            )
        } catch (e: JSONException) {
            CrashReporter.logException(e)
            return null
        }
    }

}