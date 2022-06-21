package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.files.R

class GDriveFile(
    val fileId: String,
    override val label: String,
    path: String,
    mimeType: String,
    size: Long,
    isDirectory: Boolean,
    metaData: List<Pair<Int, String>>,
    val directoryColor: String?,
    val viewUri: String
) : File(0, path, mimeType, size, isDirectory, metaData) {

    override val key: String = "gdrive://$fileId"

    override val isStoredInCloud = true

    override val providerIconRes = R.drawable.ic_badge_gdrive

    override fun getLaunchIntent(context: Context): Intent? {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(viewUri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}