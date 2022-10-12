package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.Searchable

interface IconProvider {
    suspend fun getIcon(searchable: PinnableSearchable, size: Int): LauncherIcon?
}

internal suspend fun Iterable<IconProvider>.getFirstIcon(
    searchable: PinnableSearchable,
    size: Int
): LauncherIcon? {
    for (provider in this) {
        val icon = provider.getIcon(searchable, size)
        if (icon != null) {
            return icon
        }
    }
    return null
}