package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.customattrs.CustomIconPackIcon
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.Searchable

class CustomIconPackIconProvider(
    private val customIcon: CustomIconPackIcon,
    private val iconPackManager: IconPackManager,
) : IconProvider {
    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon? {
        if (searchable !is LauncherApp) return null
        return iconPackManager.getIcon(
            customIcon.iconPackPackage,
            searchable.launcherActivityInfo.componentName
        )
    }
}