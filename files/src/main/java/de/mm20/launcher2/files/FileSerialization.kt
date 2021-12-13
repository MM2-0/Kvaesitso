package de.mm20.launcher2.files

import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.*
import org.json.JSONObject

class LocalFileSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
        searchable as LocalFile
        return jsonObjectOf(
            "id" to searchable.id
        ).toString()
    }

    override val typePrefix: String
        get() = "file"
}

class LocalFileDeserializer(
    val context: Context
) : SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
        if (!PermissionsManager.checkPermission(
                context,
                PermissionsManager.EXTERNAL_STORAGE
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
            val mimeType = cursor.getStringOrNull(3)
                ?: if (directory) "inode/directory" else LocalFile.getMimetypeByFileExtension(
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

class GDriveFileSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
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
                        R.string.file_meta_owner -> "owner"
                        R.string.file_meta_dimensions -> "dimensions"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "gdrive"
}

class GDriveFileDeserializer : SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
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
        val metaData = mutableListOf<Pair<Int, String>>()
        owner.takeIf { it.isNotEmpty() }?.let { metaData.add(R.string.file_meta_owner to it) }
        dimensions.takeIf { it.isNotEmpty() }
            ?.let { metaData.add(R.string.file_meta_dimensions to it) }
        return GDriveFile(
            fileId = id,
            label = label,
            path = path,
            mimeType = mimeType,
            size = size,
            directoryColor = color,
            isDirectory = directory,
            viewUri = uri,
            metaData = metaData
        )
    }
}

class OneDriveFileSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
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
                        R.string.file_meta_owner -> "owner"
                        R.string.file_meta_dimensions -> "dimensions"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "onedrive"
}

class OneDriveFileDeserializer : SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
        val json = JSONObject(serialized)
        val fileId = json.getString("id")
        val label = json.getString("label")
        val mimeType = json.getString("mimeType")
        val size = json.getLong("size")
        val isDirectory = json.getBoolean("directory")
        val webUrl = json.getString("webUrl")
        val owner = json.optString("owner")
        val dimensions = json.optString("dimensions")
        val metaData = mutableListOf<Pair<Int, String>>()
        owner.takeIf { it.isNotEmpty() }?.let { metaData.add(R.string.file_meta_owner to it) }
        dimensions.takeIf { it.isNotEmpty() }
            ?.let { metaData.add(R.string.file_meta_dimensions to it) }
        return OneDriveFile(
            fileId = fileId,
            label = label,
            path = "",
            mimeType = mimeType,
            size = size,
            isDirectory = isDirectory,
            metaData = metaData,
            webUrl = webUrl
        )
    }
}

class NextcloudFileSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
        searchable as NextcloudFile
        return jsonObjectOf(
            "id" to searchable.id,
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
                        R.string.file_meta_owner -> "owner"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "nextcloud"
}

class NextcloudFileDeserializer : SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
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
            metaData = owner?.let { listOf(R.string.file_meta_owner to it) } ?: emptyList()

        )
    }
}

class OwncloudFileSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
        searchable as OwncloudFile
        return jsonObjectOf(
            "id" to searchable.id,
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
                        R.string.file_meta_owner -> "owner"
                        else -> "other"
                    }, v
                )
            }
        }.toString()
    }

    override val typePrefix: String
        get() = "owncloud"
}

class OwncloudFileDeserializer : SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
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
            metaData = owner?.let { listOf(R.string.file_meta_owner to it) } ?: emptyList()

        )
    }
}