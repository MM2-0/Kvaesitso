package de.mm20.launcher2.badges.providers

import android.content.Context
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class PluginBadgeProvider(private val context: Context): BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable !is SavableSearchable) return flowOf(null)
        return flow {
            emit(null)
            val icon = searchable.getProviderIcon(context)
            if (icon != null) {
                emit(MutableBadge(icon = BadgeIcon(icon)))
            }
        }
    }
}