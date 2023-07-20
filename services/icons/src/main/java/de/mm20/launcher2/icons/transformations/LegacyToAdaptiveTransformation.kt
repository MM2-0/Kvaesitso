package de.mm20.launcher2.icons.transformations

import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import de.mm20.launcher2.icons.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LegacyToAdaptiveTransformation(
    private val foregroundScale: Float = 0.7f,
    private val backgroundColor: Int = 1,
): LauncherIconTransformation {
    override suspend fun transform(icon: StaticLauncherIcon): StaticLauncherIcon {
        if (icon.backgroundLayer !is TransparentLayer) return icon

        val bgColor = if (backgroundColor == 1) extractColor(icon.foregroundLayer) else backgroundColor
        return StaticLauncherIcon(
            foregroundLayer = scale(icon.foregroundLayer, foregroundScale),
            backgroundLayer = ColorLayer(bgColor)
        )
    }

    private fun scale(layer: LauncherIconLayer, scale: Float): LauncherIconLayer {
        return when(layer) {
            is ClockLayer -> layer.copy(scale = scale)
            is StaticIconLayer -> layer.copy(scale = scale)
            is TintedClockLayer -> layer.copy(scale = scale)
            is TintedIconLayer -> layer.copy(scale = scale)
            else -> layer
        }
    }

    private suspend fun extractColor(layer: LauncherIconLayer): Int {

        if (layer is StaticIconLayer) {
            val drawable = layer.icon

            val palette = withContext(Dispatchers.Default) {
                val bitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    drawable.toBitmap(48, 48)
                }
                Palette.from(bitmap).generate()
            }
            return palette.getDominantColor(0)
        } else if (layer is ColorLayer) {
            return layer.color
        }
        return 0
    }
}