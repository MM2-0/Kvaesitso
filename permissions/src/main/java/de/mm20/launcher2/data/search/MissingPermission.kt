package de.mm20.launcher2.search.data

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.R

class MissingPermission(override val label: String, val permissionGroup: Int): Searchable() {
    override val key: String
        get() = "permission://${permissionGroup}"

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        return LauncherIcon(
                foreground = ContextCompat.getDrawable(context, R.drawable.ic_permission)!!,
                background = ColorDrawable(ContextCompat.getColor(context, R.color.bluegrey))
        )
    }
}