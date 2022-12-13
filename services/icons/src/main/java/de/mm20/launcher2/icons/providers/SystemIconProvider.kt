package de.mm20.launcher2.icons.providers

import android.content.Context
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable

class SystemIconProvider(
    private val context: Context,
    private val themedIcons: Boolean,
) : IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        return searchable.loadIcon(context, size, themedIcons)
    }
}