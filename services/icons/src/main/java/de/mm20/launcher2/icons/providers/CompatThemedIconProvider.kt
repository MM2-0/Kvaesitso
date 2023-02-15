package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.LauncherApp

class CompatThemedIconProvider(
    private val iconPackManager: IconPackManager,
): IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        if (searchable !is LauncherApp) return null
        val component = ComponentName(searchable.`package`, searchable.activity)
        return iconPackManager.getCompatThemedIcon(component)
    }
}