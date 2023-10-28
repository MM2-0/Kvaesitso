package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.compat.AdaptiveIconDrawableCompat
import de.mm20.launcher2.icons.compat.toLauncherIcon
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompatIconProvider(
    private val context: Context,
    private val themed: Boolean = false,
) : IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        if (searchable !is Application) return null
        val component = searchable.componentName

        val icon = withContext(Dispatchers.IO) {
            val activityInfo = try {
                context.packageManager.getActivityInfo(component, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext null
            }
            val iconRes = activityInfo.iconResource
            val resources = try {
                context.packageManager.getResourcesForApplication(activityInfo.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext null
            }
            AdaptiveIconDrawableCompat.from(resources, iconRes)
        } ?: return null

        return icon.toLauncherIcon(themed = themed)
    }
}