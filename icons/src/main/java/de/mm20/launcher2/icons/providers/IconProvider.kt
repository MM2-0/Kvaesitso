package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable

interface IconProvider {
    suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon?
}