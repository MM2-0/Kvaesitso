package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.files.R
import de.mm20.launcher2.ktx.tryStartActivity

data class OwncloudFile(
        val fileId: Long,
        override val label: String,
        override val path: String,
        override val mimeType: String,
        override val size: Long,
        override val isDirectory: Boolean,
        val server: String,
        override val metaData: List<Pair<Int, String>>,
        override val labelOverride: String? = null,
) : File {

    override fun overrideLabel(label: String): OwncloudFile {
        return this.copy(labelOverride = label)
    }

    override val domain: String = Domain

    override val key: String = "$domain://$server/$fileId"

    override val isStoredInCloud: Boolean
        get() = true

    override val providerIconRes = R.drawable.ic_badge_owncloud

    private fun getLaunchIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("$server/f/$fileId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(), options)
    }

    companion object {
        const val Domain = "owncloud"
    }
}