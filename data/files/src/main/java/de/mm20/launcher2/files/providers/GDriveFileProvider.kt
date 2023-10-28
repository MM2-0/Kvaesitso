package de.mm20.launcher2.files.providers

import android.content.Context
import de.mm20.launcher2.files.R
import de.mm20.launcher2.gservices.DriveFileMeta
import de.mm20.launcher2.gservices.GoogleApiHelper
import de.mm20.launcher2.search.File

internal class GDriveFileProvider(
    private val context: Context
) : FileProvider {
    override suspend fun search(query: String): List<File> {
        if (query.length < 4) return emptyList()
        val driveFiles = GoogleApiHelper.getInstance(context).queryGDriveFiles(query)
        return driveFiles.map {
            GDriveFile(
                fileId = it.fileId,
                label = it.label,
                size = it.size,
                mimeType = it.mimeType,
                isDirectory = it.isDirectory,
                path = "",
                directoryColor = it.directoryColor,
                viewUri = it.viewUri,
                metaData = getMetadata(it.metadata)
            )
        }
    }

    private fun getMetadata(file: DriveFileMeta): List<Pair<Int, String>> {
        val metaData = mutableListOf<Pair<Int, String>>()
        val owners = file.owners
        metaData.add(R.string.file_meta_owner to owners.joinToString(separator = ", "))
        val width = file.width ?: file.width
        val height = file.height ?: file.height
        if (width != null && height != null) metaData.add(R.string.file_meta_dimensions to "${width}x$height")
        return metaData
    }
}