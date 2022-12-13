package de.mm20.launcher2.icons.transformations

import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransformableDynamicLauncherIcon

internal interface LauncherIconTransformation {
    suspend fun transform(icon: StaticLauncherIcon): StaticLauncherIcon
}

internal suspend fun LauncherIcon.transform(transformations: Iterable<LauncherIconTransformation>): LauncherIcon {
    if (this is StaticLauncherIcon) {
        var transformedIcon = this
        for (transformation in transformations) {
            transformedIcon = transformation.transform(transformedIcon as StaticLauncherIcon)
        }
        return transformedIcon
    }
    if (this is TransformableDynamicLauncherIcon) {
        this.setTransformations(transformations.toList())
        return this
    }
    return this
}