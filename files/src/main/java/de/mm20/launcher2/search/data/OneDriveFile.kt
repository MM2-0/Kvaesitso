package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.msservices.DriveItem
import de.mm20.launcher2.files.R
import de.mm20.launcher2.msservices.MicrosoftGraphApiHelper
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.LauncherPreferences

class OneDriveFile(
        val fileId: String,
        override val label: String,
        path: String,
        mimeType: String,
        size: Long,
        isDirectory: Boolean,
        metaData: List<Pair<Int, String>>,
        val webUrl: String
) : File(0, path, mimeType, size, isDirectory, metaData) {

    override val badgeKey: String = "onedrive://"

    override val key: String = "onedrive://$fileId"

    override val isStoredInCloud = true

    override suspend fun loadIcon(context: Context, size: Int): LauncherIcon? {
        return null
    }

    override fun getLaunchIntent(context: Context): Intent? {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(webUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    companion object {
        suspend fun search(context: Context, query: String): List<File> {
            if (query.length < 4) return emptyList()
            if (!LauncherPreferences.instance.searchOneDrive) return emptyList()
            val driveItems = MicrosoftGraphApiHelper.getInstance(context).queryOneDriveFiles(query) ?: return emptyList()
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
            return files.sorted()
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
}