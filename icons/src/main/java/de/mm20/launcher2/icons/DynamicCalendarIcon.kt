package de.mm20.launcher2.icons

import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Transformations
import de.mm20.launcher2.icons.transformations.LauncherIconTransformation
import java.time.Instant
import java.time.ZoneId

internal class DynamicCalendarIcon(
    val resources: Resources,
    val resourceIds: IntArray,
    val isThemed: Boolean = false,
    private var transformations: List<LauncherIconTransformation> = emptyList(),
) : DynamicLauncherIcon, TransformableDynamicLauncherIcon {

    init {
        if (resourceIds.size < 31) throw IllegalArgumentException("DynamicCalendarIcon resourceIds must at least have 31 items")
    }

    override suspend fun getIcon(time: Long): StaticLauncherIcon {
        val day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).dayOfMonth
        val resId = resourceIds[day - 1]

        val drawable = try {
            ResourcesCompat.getDrawable(resources, resId, null)
        } catch (e: Resources.NotFoundException) {
            null
        } ?: return StaticLauncherIcon(
            foregroundLayer = TextLayer(day.toString()),
            backgroundLayer = ColorLayer()
        )

        var icon = if (isThemed) {
            StaticLauncherIcon(
                foregroundLayer = TintedIconLayer(
                    icon = drawable,
                    scale = 0.5f,
                ),
                backgroundLayer = ColorLayer()
            )
        } else if (drawable is AdaptiveIconDrawable) {
            return StaticLauncherIcon(
                foregroundLayer = drawable.foreground?.let {
                    StaticIconLayer(
                        icon = it,
                        scale = 1.5f,
                    )
                } ?: TransparentLayer,
                backgroundLayer = drawable.background?.let {
                    StaticIconLayer(
                        icon = it,
                        scale = 1.5f,
                    )
                } ?: TransparentLayer,
            )
        } else StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(
                icon = drawable,
                scale = 1f,
            ),
            backgroundLayer = TransparentLayer
        )

        for (transformation in transformations) {
            icon = transformation.transform(icon)
        }
        return icon
    }

    override fun setTransformations(transformations: List<LauncherIconTransformation>) {
        this.transformations = transformations
    }
}