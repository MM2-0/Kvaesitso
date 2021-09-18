package de.mm20.launcher2.ui.legacy.data

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import com.amulyakhare.textdrawable.TextDrawable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable

/**
 * A helper searchable that is used to display text inside [de.mm20.launcher2.ui.search2.SearchGridViews]
 */
class InformationText(
        override val label: String,
        val clickAction: (() -> Unit)? = null
) : Searchable() {

    override val key: String
        get() = "text://${label}"

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        return LauncherIcon(
                foreground = TextDrawable.builder()
                        .buildRect("i", Color.WHITE),
                background = ColorDrawable(ContextCompat.getColor(context, R.color.grey))
        )
    }
}