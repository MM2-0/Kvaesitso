package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.data.customattrs.LegacyCustomIconPackIcon
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable

@Deprecated("Use CustomIconPackIconProvider instead")
class LegacyCustomIconPackIconProvider(
    private val customIcon: LegacyCustomIconPackIcon,
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