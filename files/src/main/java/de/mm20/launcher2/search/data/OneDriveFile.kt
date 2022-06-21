package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.files.R

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

    override val key: String = "onedrive://$fileId"

    override val providerIconRes = R.drawable.ic_badge_onedrive

    override val isStoredInCloud = true

    override fun getLaunchIntent(context: Context): Intent? {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(webUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}