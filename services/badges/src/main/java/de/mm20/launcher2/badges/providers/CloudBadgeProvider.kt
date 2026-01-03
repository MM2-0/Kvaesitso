package de.mm20.launcher2.badges.providers

import android.content.Context
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class CloudBadgeProvider(
    private val context: Context
) : BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable is File) {
            val iconResId = searchable.providerIconRes
            if (iconResId != null) {
                return flowOf(MutableBadge(icon = BadgeIcon(context.getDrawable(iconResId)!!)))
            }
        }
        return flowOf(null)
    }
}