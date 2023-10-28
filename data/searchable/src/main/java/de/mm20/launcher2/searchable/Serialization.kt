package de.mm20.launcher2.searchable

import de.mm20.launcher2.search.SavableSearchable

internal fun SavableSearchable.serialize(): String? {
    val serializer = getSerializer()
    return serializer.serialize(this)
}
