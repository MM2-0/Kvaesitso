package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable

interface IconProvider {
    suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon?
}

internal suspend fun Iterable<IconProvider>.getFirstIcon(
    searchable: SavableSearchable,
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