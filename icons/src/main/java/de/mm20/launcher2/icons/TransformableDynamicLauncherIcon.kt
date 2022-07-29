package de.mm20.launcher2.icons

import de.mm20.launcher2.icons.transformations.LauncherIconTransformation

internal interface TransformableDynamicLauncherIcon {
    fun setTransformations(transformations: List<LauncherIconTransformation>)
}