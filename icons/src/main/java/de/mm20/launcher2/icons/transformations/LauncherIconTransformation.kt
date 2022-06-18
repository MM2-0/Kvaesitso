package de.mm20.launcher2.icons.transformations

import de.mm20.launcher2.icons.StaticLauncherIcon

internal interface LauncherIconTransformation {
    suspend fun transform(icon: StaticLauncherIcon): StaticLauncherIcon
}