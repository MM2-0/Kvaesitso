package de.mm20.launcher2.icons.providers

import android.content.Context
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable

class PlaceholderIconProvider(val context: Context) : IconProvider {
    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon {
        return searchable.getPlaceholderIcon(context)
    }
}