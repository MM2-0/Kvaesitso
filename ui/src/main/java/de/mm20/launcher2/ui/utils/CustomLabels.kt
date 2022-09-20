package de.mm20.launcher2.ui.utils

import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.customattrs.CustomLabel
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

fun <T : Searchable> Flow<List<T>>.withCustomLabels(
    customAttributesRepository: CustomAttributesRepository,
): Flow<List<T>> = channelFlow {
    this@withCustomLabels.collectLatest { items ->
        val customLabels = customAttributesRepository.getCustomLabels(items)
        customLabels.collectLatest { labels ->
            for (item in items) {
                val customLabel = labels.find { it.key == item.key }
                item.labelOverride = customLabel?.label
            }
            send(items)
        }
    }
}