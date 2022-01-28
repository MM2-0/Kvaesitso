package de.mm20.launcher2.icons.providers

import android.content.Context
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.search.data.Searchable

class SystemIconProvider(
    private val context: Context,
    private val legacyIconBackground: Settings.IconSettings.LegacyIconBackground
    ) : IconProvider {
    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon? {
        return searchable.loadIcon(context, size, legacyIconBackground)
    }
}