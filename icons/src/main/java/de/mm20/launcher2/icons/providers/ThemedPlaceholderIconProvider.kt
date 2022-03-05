package de.mm20.launcher2.icons.providers

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.R
import de.mm20.launcher2.icons.ThemeColors
import de.mm20.launcher2.search.data.Searchable

internal class ThemedPlaceholderIconProvider(
    private val context: Context,
    private val colors: ThemeColors,
) : IconProvider {

    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon {
        val icon = searchable.getPlaceholderIcon(context)

        icon.foreground.setTint(colors.foreground)
        icon.background?.setTint(colors.background)
        return icon
    }

}