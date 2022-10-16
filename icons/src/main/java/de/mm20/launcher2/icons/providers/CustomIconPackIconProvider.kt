package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import de.mm20.launcher2.customattrs.CustomIconPackIcon
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
            ComponentName.unflattenFromString(customIcon.iconComponentName) ?: return null
        )
    }
}