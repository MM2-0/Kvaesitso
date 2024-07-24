package de.mm20.launcher2.icons.providers

import android.content.Context
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.search.SavableSearchable

internal class ThemedPlaceholderIconProvider(
    private val context: Context,
) : IconProvider {

    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon {
        val icon = searchable.getPlaceholderIcon(context)

        return StaticLauncherIcon(
            foregroundLayer = asThemed(icon.foregroundLayer),
            backgroundLayer = asThemed(icon.backgroundLayer),
        )
    }

    private fun asThemed(layer: LauncherIconLayer): LauncherIconLayer {
        return when (layer) {
            is ClockLayer -> TintedClockLayer(
                scale = layer.scale,
                color = 0,
                defaultHour = layer.defaultHour,
                defaultMinute = layer.defaultMinute,
                defaultSecond = layer.defaultSecond,
                sublayers = layer.sublayers,
            )
            is ColorLayer -> layer.copy(color = 0)
            is StaticIconLayer -> TintedIconLayer(
                icon = layer.icon,
                color = 0,
                scale = layer.scale,
            )
            is VectorLayer -> layer.copy(color = 0)
            is TextLayer -> layer.copy(color = 0)
            is TintedIconLayer -> layer.copy(color = 0)
            is TintedClockLayer -> return layer.copy(color = 0)
            is TransparentLayer -> return layer
        }
    }

}