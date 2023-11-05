package de.mm20.launcher2.files.providers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.files.NextcloudFileSerializer
import de.mm20.launcher2.files.R
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.ImmutableMap

internal data class NextcloudFile(
    val fileId: Long,
    override val label: String,
    override val path: String,
    override val mimeType: String,
    override val size: Long,
    override val isDirectory: Boolean,
    val server: String,
    override val metaData: ImmutableMap<FileMetaType, String>,
    override val labelOverride: String? = null,
) : File {

    override fun overrideLabel(label: String): NextcloudFile {
        return this.copy(labelOverride = label)
    }

    override val domain: String = Domain

    override val key: String = "$domain://$server/$fileId"

    override val providerIconRes = R.drawable.ic_badge_nextcloud

    private fun getLaunchIntent(context: Context): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("$server/f/$fileId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            `package` = getNextcloudAppPackage(context)
        }
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(context), options)
    }

    override fun getSerializer(): SearchableSerializer {
        return NextcloudFileSerializer()
    }

    companion object {

        const val Domain = "nextcloud"
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