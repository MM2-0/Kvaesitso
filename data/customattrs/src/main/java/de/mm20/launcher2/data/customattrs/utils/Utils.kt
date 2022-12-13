package de.mm20.launcher2.data.customattrs.utils

import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

fun <T: SavableSearchable>Flow<List<T>>.withCustomLabels(
    customAttributesRepository: CustomAttributesRepository,
): Flow<List<T>> = channelFlow {
    this@withCustomLabels.collectLatest { items ->
        val customLabels = customAttributesRepository.getCustomLabels(items)
        customLabels.collectLatest { labels ->
            send(items.map { item ->
                    val customLabel = labels.find { it.key == item.key }
                    if (customLabel != null) {
                        item.overrideLabel(customLabel.label) as T
                    } else {
                        item
                    }
            })
        }
    }
}