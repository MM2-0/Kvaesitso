package de.mm20.launcher2.icons.providers

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.R
import de.mm20.launcher2.search.data.Searchable

class ThemedPlaceholderIconProvider(
    val context: Context
) : IconProvider {

    private val fgColor: Int
    private val bgColor: Int

    init {
        val theme = context.resources.newTheme()
        theme.applyStyle(R.style.DefaultColors, true)
        val typedValue = TypedValue()
        val isDarkMode =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES != 0

        val bgAttr = R.attr.colorPrimaryContainer
        val fgAttr = R.attr.colorOnPrimaryContainer

        bgColor = theme.resolveAttribute(bgAttr, typedValue, true).let {
            typedValue.data
        }
        fgColor = theme.resolveAttribute(fgAttr, typedValue, true).let {
            typedValue.data
        }
    }

    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon {


        val icon = searchable.getPlaceholderIcon(context)

        icon.foreground.setTint(fgColor)
        icon.background?.setTint(bgColor)
        return icon
    }

}