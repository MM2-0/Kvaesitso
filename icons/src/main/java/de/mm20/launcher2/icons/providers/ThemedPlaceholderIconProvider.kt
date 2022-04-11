package de.mm20.launcher2.icons.providers

import android.content.Context
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable

internal class ThemedPlaceholderIconProvider(
    private val context: Context,
) : IconProvider {

    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon {
        val icon = searchable.getPlaceholderIcon(context)

        return LauncherIcon(
            foreground = icon.foreground,
            foregroundScale = icon.foregroundScale,
            background = icon.background,
            backgroundScale = icon.backgroundScale,
            isThemeable = true
        )
    }

}