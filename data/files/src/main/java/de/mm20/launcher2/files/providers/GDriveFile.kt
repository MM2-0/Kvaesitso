package de.mm20.launcher2.files.providers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.files.GDriveFileSerializer
import de.mm20.launcher2.files.R
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.ImmutableMap

internal data class GDriveFile(
    val fileId: String,
    override val label: String,
    override val path: String,
    override val mimeType: String,
    override val size: Long,
    override val isDirectory: Boolean,
    override val metaData: ImmutableMap<FileMetaType, String>,
    val directoryColor: String?,
    val viewUri: String,
    override val labelOverride: String? = null,
) : File {

    override fun overrideLabel(label: String): GDriveFile {
        return this.copy(labelOverride = label)
    }

    override val domain: String = Domain

    override val key: String = "$domain://$fileId"

    override val providerIconRes = R.drawable.ic_badge_gdrive

    private fun getLaunchIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(viewUri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(), options)
    }

    override fun getSerializer(): SearchableSerializer {
        return GDriveFileSerializer()
    }

    companion object {
        const val Domain = "gdrive"
    }
}