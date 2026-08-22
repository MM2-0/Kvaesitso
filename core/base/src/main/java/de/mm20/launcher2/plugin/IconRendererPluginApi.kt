package de.mm20.launcher2.plugin

import android.content.ComponentName
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.plugin.contracts.IconRendererPluginContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconRendererPluginApi(
    private val authority: String,
    private val contentResolver: ContentResolver,
) {
    suspend fun renderIcon(componentName: ComponentName, size: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val extras = Bundle().apply {
                putString(
                    IconRendererPluginContract.Extras.PackageName,
                    componentName.packageName,
                )
                putString(
                    IconRendererPluginContract.Extras.ActivityName,
                    componentName.className,
                )
                putInt(IconRendererPluginContract.Extras.Size, size)
            }
            val result = try {
                contentResolver.call(
                    Uri.Builder().scheme("content").authority(authority).build(),
                    IconRendererPluginContract.Methods.RenderIcon,
                    null,
                    extras,
                )
            } catch (_: Exception) {
                null
            } ?: return@withContext null
            val png = result.getByteArray(IconRendererPluginContract.Extras.Png)
                ?: return@withContext null
            BitmapFactory.decodeByteArray(png, 0, png.size)
        }
}
