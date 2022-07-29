package de.mm20.launcher2.icons.transformations

import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransformableDynamicLauncherIcon

internal interface LauncherIconTransformation {
    suspend fun transform(icon: StaticLauncherIcon): StaticLauncherIcon
}

internal suspend fun Iterable<LauncherIconTransformation>.apply(icon: LauncherIcon): LauncherIcon {
    if (icon is StaticLauncherIcon) {
        var transformedIcon = icon
        for (transformation in this) {
            transformedIcon = transformation.transform(transformedIcon as StaticLauncherIcon)
        }
        return transformedIcon
    }
    if (icon is TransformableDynamicLauncherIcon) {
        icon.setTransformations(this.toList())
        return icon
    }
    return icon
}