package de.mm20.launcher2.files.providers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.files.PluginFileSerializer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.ImmutableMap

data class PluginFile(
    val id: String,
    override val path: String,
    override val mimeType: String,
    override val size: Long,
    override val metaData: ImmutableMap<FileMetaType, String>,
    override val label: String,
    override val isDirectory: Boolean,
    val uri: Uri,
    val thumbnailUri: Uri?,
    val authority: String,
    internal val storageStrategy: StorageStrategy,
    override val labelOverride: String? = null,
) : File {
    override val domain: String = Domain

    override val key: String
        get() = "$domain://$authority:$id"

    override fun overrideLabel(label: String): SavableSearchable {
        return this.copy(labelOverride = label)
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(Intent(Intent.ACTION_VIEW).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }, options)
    }

    override fun getSerializer(): SearchableSerializer {
        return PluginFileSerializer()
    }

    companion object {
        const val Domain = "plugin.file"
    }
}