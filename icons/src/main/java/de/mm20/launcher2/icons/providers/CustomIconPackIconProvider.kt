package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import de.mm20.launcher2.customattrs.CustomIconPackIcon
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.Searchable

class CustomIconPackIconProvider(
    private val customIcon: CustomIconPackIcon,
    private val iconPackManager: IconPackManager,
) : IconProvider {
    override suspend fun getIcon(searchable: PinnableSearchable, size: Int): LauncherIcon? {
        return iconPackManager.getIcon(
            customIcon.iconPackPackage,
            ComponentName.unflattenFromString(customIcon.iconComponentName) ?: return null
        )
    }
}