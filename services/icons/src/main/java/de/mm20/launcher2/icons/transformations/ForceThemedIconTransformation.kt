package de.mm20.launcher2.icons.transformations

import de.mm20.launcher2.icons.*

internal class ForceThemedIconTransformation : LauncherIconTransformation {
    override suspend fun transform(icon: StaticLauncherIcon): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = asThemed(icon.foregroundLayer),
            backgroundLayer = ColorLayer(0),
        )
    }

    private fun asThemed(layer: LauncherIconLayer): LauncherIconLayer {
        return when(layer) {
            is ClockLayer -> TintedClockLayer(
                scale = layer.scale,
                defaultHour = layer.defaultHour,
                defaultMinute = layer.defaultMinute,
                defaultSecond = layer.defaultSecond,
                sublayers = layer.sublayers,
            )
            is ColorLayer -> layer.copy(color = 0)
            is StaticIconLayer -> TintedIconLayer(
                color = 0,
                icon = layer.icon,
                scale = layer.scale / 1.2f,
            )
            is TextLayer -> layer.copy(
                color = 0
            )
            else -> layer
        }
    }

}