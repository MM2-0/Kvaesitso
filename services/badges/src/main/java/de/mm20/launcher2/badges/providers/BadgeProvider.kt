package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow

interface BadgeProvider {
    /**
     * This must emit a value as soon as possible because the
     * BadgeRepository is waiting for values from every provider.
     * null must be emitted if no badge should be shown.
     */
    fun getBadge(searchable: Searchable): Flow<Badge?>
}