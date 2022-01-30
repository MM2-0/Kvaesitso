package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.files.R
import de.mm20.launcher2.helper.NetworkUtils
import de.mm20.launcher2.owncloud.OwncloudClient

class OwncloudFile(
        fileId: Long,
        override val label: String,
        path: String,
        mimeType: String,
        size: Long,
        isDirectory: Boolean,
        val server: String,
        metaData: List<Pair<Int, String>>
) : File(fileId, path, mimeType, size, isDirectory, metaData) {
    override val badgeKey: String = "owncloud://"

    override val key: String = "owncloud://$server/$fileId"

    override val isStoredInCloud: Boolean
        get() = true

    override fun getLaunchIntent(context: Context): Intent? {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("$server/f/$id")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}