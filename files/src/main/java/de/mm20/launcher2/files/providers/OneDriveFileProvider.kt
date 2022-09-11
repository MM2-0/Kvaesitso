package de.mm20.launcher2.files.providers

import android.content.Context
import de.mm20.launcher2.files.R
import de.mm20.launcher2.msservices.DriveItem
import de.mm20.launcher2.msservices.MicrosoftGraphApiHelper
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.OneDriveFile

internal class OneDriveFileProvider(
    private val context: Context
) : FileProvider {
    override suspend fun search(query: String): List<File> {
        if (query.length < 4) return emptyList()
        val driveItems = MicrosoftGraphApiHelper.getInstance(context).queryOneDriveFiles(query)
            ?: return emptyList()
        val files = mutableListOf<OneDriveFile>()
        for (driveItem in driveItems) {
            files += OneDriveFile(
                fileId = driveItem.id,
                label = driveItem.label,
                path = "",
                mimeType = driveItem.mimeType,
                size = driveItem.size,
                isDirectory = driveItem.isDirectory,
                metaData = getMetaData(driveItem),
                webUrl = driveItem.webUrl
            )
        }
        return files
    }

    private fun getMetaData(driveItem: DriveItem): List<Pair<Int, String>> {
        val metaData = mutableListOf<Pair<Int, String>>()
        driveItem.meta.owner?.let {
            metaData.add(R.string.file_meta_owner to it)
        } ?: driveItem.meta.createdBy?.let {
            metaData.add(R.string.file_meta_owner to it)
        }
        val width = driveItem.meta.width
        val height = driveItem.meta.height

        if (width != null && height != null) {
            metaData.add(R.string.file_meta_dimensions to "${width}x${height}")
        }
        return metaData
    }
}