package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

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
            `package` = getNextcloudAppPackage(context)
        }
    }

    companion object {
        private fun getNextcloudAppPackage(context: Context): String? {
            val candidates = listOf("com.nextcloud.client", "com.nextcloud.android.beta")

            for (c in candidates) {
                try {
                    context.packageManager.getPackageInfo(c, 0)
                    return c
                } catch (e: PackageManager.NameNotFoundException) {
                }
            }
            return null
        }
    }
}