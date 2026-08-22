package de.mm20.launcher2.icons.providers

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.plugin.IconRendererPluginApi
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable

internal class PluginIconRendererProvider(
    private val context: Context,
    private val plugin: Plugin,
) : IconProvider {
    private val pluginApi = IconRendererPluginApi(plugin.authority, context.contentResolver)

    override suspend fun getIcon(searchable: SavableSearchable, size: Int): StaticLauncherIcon? {
        val app = searchable as? Application ?: return null
        val bitmap = pluginApi.renderIcon(app.componentName, size) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(BitmapDrawable(context.resources, bitmap)),
            backgroundLayer = TransparentLayer,
        )
    }
}
