package de.mm20.launcher2.sdk.icons

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Bundle
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.IconRendererPluginContract
import de.mm20.launcher2.sdk.base.BasePluginProvider
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

abstract class IconRendererProvider : BasePluginProvider() {
    final override fun getPluginType() = PluginType.IconRenderer

    final override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != IconRendererPluginContract.Methods.RenderIcon) {
            return super.call(method, arg, extras)
        }
        val context = context ?: return null
        checkPermissionOrThrow(context)
        val packageName = extras?.getString(IconRendererPluginContract.Extras.PackageName)
            ?: return null
        val activityName = extras.getString(IconRendererPluginContract.Extras.ActivityName)
            ?: return null
        val size = extras.getInt(IconRendererPluginContract.Extras.Size).coerceAtLeast(1)
        val bitmap = runBlocking {
            renderIcon(ComponentName(packageName, activityName), size)
        } ?: return null
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return Bundle().apply {
            putByteArray(IconRendererPluginContract.Extras.Png, output.toByteArray())
        }
    }

    abstract suspend fun renderIcon(componentName: ComponentName, size: Int): Bitmap?
}
