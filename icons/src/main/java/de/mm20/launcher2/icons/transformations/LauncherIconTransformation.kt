package de.mm20.launcher2.icons.transformations

import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon

internal interface LauncherIconTransformation {
    suspend fun transform(icon: StaticLauncherIcon): StaticLauncherIcon
}

internal suspend fun Iterable<LauncherIconTransformation>.apply(icon: LauncherIcon): LauncherIcon {
    var transformedIcon = icon
    if (transformedIcon is StaticLauncherIcon) {
        for (transformation in this) {
            transformedIcon = transformation.transform(transformedIcon as StaticLauncherIcon)
        }
    }
    return transformedIcon
}