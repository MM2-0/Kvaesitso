package de.mm20.launcher2.icons

import android.content.res.Resources

sealed interface LauncherIcon

data class StaticLauncherIcon(
    val foregroundLayer: LauncherIconLayer,
    val backgroundLayer: LauncherIconLayer,
): LauncherIcon

interface DynamicLauncherIcon: LauncherIcon {
    suspend fun getIcon(time: Long): StaticLauncherIcon
}