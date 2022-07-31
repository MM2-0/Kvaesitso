package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.customattrs.CustomThemedIcon
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable

class CustomThemedIconProvider(
    private val customIcon: CustomThemedIcon,
    private val iconPackManager: IconPackManager,
): IconProvider {
    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon? {
        return iconPackManager.getThemedIcon(customIcon.iconPackageName)
    }
}