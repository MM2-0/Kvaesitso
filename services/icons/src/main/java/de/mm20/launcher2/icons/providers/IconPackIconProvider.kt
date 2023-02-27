package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconPackIconProvider(
    private val context: Context,
    private val iconPack: IconPack,
    private val iconPackManager: IconPackManager,
): IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        if (searchable !is LauncherApp) return null

        return iconPackManager.getIcon(iconPack.packageName, searchable.`package`, searchable.activity)
            ?: iconPackManager.generateIcon(
                context,
                iconPack.packageName,
                baseIcon = withContext(Dispatchers.IO) {
                    searchable.launcherActivityInfo.getIcon(context.resources.displayMetrics.densityDpi)
                },
                size = size,
            )
    }
}