package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.files.R
import de.mm20.launcher2.helper.NetworkUtils
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.preferences.LauncherPreferences

class NextcloudFile(
        fileId: Long,
        override val label: String,
        path: String,
        mimeType: String,
        size: Long,
        isDirectory: Boolean,
        val server: String,
        metaData: List<Pair<Int, String>>
) : File(fileId, path, mimeType, size, isDirectory, metaData) {
    override val badgeKey: String = "nextcloud://"

    override val key: String = "nextcloud://$server/$fileId"

    override val isStoredInCloud: Boolean
        get() = true

    override fun getLaunchIntent(context: Context): Intent? {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("$server/f/$id")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    companion object {
        suspend fun search(context: Context, query: String, nextcloudClient: NextcloudApiHelper) : List<NextcloudFile> {
            if (!LauncherPreferences.instance.searchNextcloud) return emptyList()
            if (query.length < 4) return emptyList()
            val server = nextcloudClient.getServer() ?: return emptyList()
            if (NetworkUtils.isOffline(context, LauncherPreferences.instance.searchGDriveMobileData)) return emptyList()
            return nextcloudClient.files.search(query).map {
                NextcloudFile(
                        fileId = it.id,
                        label = it.name,
                        path = server + it.url,
                        mimeType = it.mimeType,
                        size = it.size,
                        isDirectory = it.isDirectory,
                        server = server,
                        metaData = it.owner?.let { listOf(R.string.file_meta_owner to it) } ?: emptyList()
                )
            }
        }

    }
}