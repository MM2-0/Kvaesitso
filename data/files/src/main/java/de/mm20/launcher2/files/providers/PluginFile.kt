package de.mm20.launcher2.files.providers

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import coil.imageLoader
import coil.request.ImageRequest
import de.mm20.launcher2.files.PluginFileSerializer
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdatableSearchable
import de.mm20.launcher2.search.UpdateResult
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PluginFile(
    val id: String,
    override val path: String?,
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
    override val timestamp: Long,
    override val updatedSelf: (suspend (SavableSearchable) -> UpdateResult<File>)?,
) : File, UpdatableSearchable<File> {
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

    override suspend fun getProviderIcon(context: Context): Drawable? {
        return withContext(Dispatchers.IO) {
            context.packageManager.resolveContentProvider(authority, 0)?.loadIcon(context.packageManager)
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        if (thumbnailUri != null) {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUri)
                .build()
            val result = context.imageLoader.execute(request)
            val drawable = result.drawable ?: return null
            return StaticLauncherIcon(
                foregroundLayer = StaticIconLayer(icon = drawable, scale = 1.5f),
                backgroundLayer = ColorLayer(),
            )
        }
        return null
    }

    companion object {
        const val Domain = "plugin.file"
    }
}