package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.icons.*
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.LauncherApp

internal class ThemedIconProvider(
    private val iconPackManager: IconPackManager,
) : IconProvider {

    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        if (searchable !is LauncherApp) return null
        return iconPackManager.getThemedIcon(searchable.`package`)
    }
}