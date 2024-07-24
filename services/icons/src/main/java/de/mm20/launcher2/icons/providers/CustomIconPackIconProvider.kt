package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.data.customattrs.CustomIconPackIcon
import de.mm20.launcher2.database.entities.IconEntity
import de.mm20.launcher2.icons.IconPackAppIcon
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable

class CustomIconPackIconProvider(
    private val customIcon: CustomIconPackIcon,
    private val iconPackManager: IconPackManager,
) : IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        val ent = IconEntity(
            type = customIcon.type,
            drawable = customIcon.drawable,
            extras = customIcon.extras,
            iconPack = customIcon.iconPackPackage,
            themed = customIcon.allowThemed,
        )
        val icon = IconPackAppIcon(ent) ?: return null
        return iconPackManager.getIcon(
            customIcon.iconPackPackage,
            icon,
            customIcon.allowThemed,
        )
    }
}