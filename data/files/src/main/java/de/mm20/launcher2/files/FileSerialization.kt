package de.mm20.launcher2.files

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.files.providers.GDriveFile
import de.mm20.launcher2.files.providers.LocalFile
import de.mm20.launcher2.files.providers.NextcloudFile
import de.mm20.launcher2.files.providers.OwncloudFile
import de.mm20.launcher2.files.providers.PluginFile
import de.mm20.launcher2.files.providers.PluginFileProvider
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult
import de.mm20.launcher2.serialization.Json
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Serializable
internal data class SerializedFile(
    val id: String? = null,
    val authority: String? = null,
    val strategy: StorageStrategy? = null,
    val path: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val label: String? = null,
    val uri: String? = null,
    val thumbnailUri: String? = null,
    val isDirectory: Boolean? = null,
    val metadata: Map<FileMetaType, String>? = null,
    val timestamp: Long? = null,
)

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
        return when(searchable.storageStrategy) {
            StorageStrategy.StoreReference -> Json.Lenient.encodeToString(
                SerializedFile(
                    id = searchable.id,
                    authority = searchable.authority,
                    strategy = StorageStrategy.StoreReference
                )
            )
            else -> {
                Json.Lenient.encodeToString(
                    SerializedFile(
                        id = searchable.id,
                        path = searchable.path,
                        mimeType = searchable.mimeType,
                        size = searchable.size,
                        label = searchable.label,
                        uri = searchable.uri.toString(),
                        thumbnailUri = searchable.thumbnailUri?.toString(),
                        isDirectory = searchable.isDirectory,
                        authority = searchable.authority,
                        timestamp = searchable.timestamp,
                        metadata = searchable.metaData.toMap(),
                        strategy = StorageStrategy.StoreCopy,
                    )
                )
            }
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
        val json = Json.Lenient.decodeFromString<SerializedFile>(serialized)
        val authority = json.authority ?: return null
        val id = json.id ?: return null
        val strategy = json.strategy ?: StorageStrategy.StoreCopy

        val plugin = pluginRepository.get(authority).firstOrNull() ?: return null
        if (!plugin.enabled) return null

        return when(strategy) {
            StorageStrategy.StoreReference -> {
                PluginFileProvider(context, authority).get(id).getOrNull()
            }
            else -> {
                val timestamp = json.timestamp ?: 0
                PluginFile(
                    id = id,
                    path = json.path,
                    mimeType = json.mimeType ?: "binary/octet-stream",
                    size = json.size ?: 0,
                    metaData = json.metadata?.toImmutableMap() ?: persistentMapOf(),
                    label = json.label ?: return null,
                    uri = Uri.parse(json.uri ?: return null),
                    thumbnailUri = json.thumbnailUri?.let { Uri.parse(it) },
                    storageStrategy = StorageStrategy.StoreCopy,
                    isDirectory = json.isDirectory ?: false,
                    authority = authority,
                    timestamp = timestamp,
                    updatedSelf = {
                        if (it !is PluginFile) UpdateResult.TemporarilyUnavailable()
                        else PluginFileProvider(context, authority).refresh(it, timestamp).asUpdateResult()
                    }
                )
            }
        }
    }
}