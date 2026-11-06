package de.mm20.launcher2.data.customattrs.utils

import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun <T: SavableSearchable> Flow<List<T>>.withCustomLabels(
    customAttributesRepository: CustomAttributesRepository,
): Flow<List<T>> = flatMapLatest { items ->
    customAttributesRepository.getCustomLabels(items).map { labels ->
        items.map { item ->
            val customLabel = labels.find { it.key == item.key }
            if (customLabel != null) {
                item.overrideLabel(customLabel.label) as T
            } else {
                item
            }
        }
    }
}