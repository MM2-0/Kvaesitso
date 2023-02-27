package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.data.customattrs.CustomThemedIcon
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable

class CustomThemedIconProvider(
    private val customIcon: CustomThemedIcon,
    private val iconPackManager: IconPackManager,
): IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        return null //iconPackManager.getThemedIcon(customIcon.iconPackageName)
    }
}