package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.icons.*
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.data.LauncherApp

internal class ThemedIconProvider(
    private val iconPackManager: IconPackManager,
) : IconProvider {

    override suspend fun getIcon(searchable: PinnableSearchable, size: Int): LauncherIcon? {
        if (searchable !is LauncherApp) return null
        return iconPackManager.getThemedIcon(searchable.`package`)
    }
}