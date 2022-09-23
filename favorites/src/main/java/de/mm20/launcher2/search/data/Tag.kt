package de.mm20.launcher2.search.data

import android.content.Context
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer

class Tag(
    val tag: String,
): Searchable() {
    override val key: String = "tag://$tag"
    override val label: String = tag

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = TextLayer("#"),
            backgroundLayer = ColorLayer()
        )
    }
}