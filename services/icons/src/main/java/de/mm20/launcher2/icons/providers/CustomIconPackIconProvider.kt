package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import de.mm20.launcher2.data.customattrs.CustomIconPackIcon
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable

class CustomIconPackIconProvider(
    private val customIcon: CustomIconPackIcon,
    private val iconPackManager: IconPackManager,
) : IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        return iconPackManager.getIcon(
            customIcon.iconPackPackage,
            customIcon.iconPackageName,
            customIcon.iconActivityName,
            customIcon.allowThemed,
        )
    }
}