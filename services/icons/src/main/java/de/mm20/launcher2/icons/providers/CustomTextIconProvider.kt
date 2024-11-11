package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.data.customattrs.CustomIconPackIcon
import de.mm20.launcher2.data.customattrs.CustomTextIcon
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.search.SavableSearchable

class CustomTextIconProvider(
    private val customIcon: CustomTextIcon,
): IconProvider {
    override suspend fun getIcon(
        searchable: SavableSearchable,
        size: Int
    ): LauncherIcon? {
        return StaticLauncherIcon(
            foregroundLayer = TextLayer(
                text = customIcon.text,
                color = customIcon.color,
            ),
            backgroundLayer = ColorLayer(
                color = customIcon.color,
            ),
        )
    }

}