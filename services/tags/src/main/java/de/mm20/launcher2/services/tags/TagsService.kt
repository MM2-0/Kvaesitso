package de.mm20.launcher2.services.tags

import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TagsService(
    private val customAttributesRepository: CustomAttributesRepository,
    private val searchableRepository: SavableSearchableRepository,
) {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    fun getAllTags(startsWith: String? = null): Flow<List<String>> {
        return customAttributesRepository.getAllTags(startsWith)
    }

    fun deleteTag(tag: String) {
        searchableRepository.delete(Tag(tag))
        customAttributesRepository.deleteTag(tag)
    }

    fun cloneTag(tag: String, newTag: String) {
        scope.launch {
            val items = getTaggedItems(tag).first()
            createTag(newTag, items)
        }
    }

    fun getTaggedItems(tag: String): Flow<List<SavableSearchable>> {
        return customAttributesRepository.getItemsForTag(tag)
    }

    fun updateTag(tag: String, newName: String? = null, items: List<SavableSearchable>? = null) {
        scope.launch {
            if (items != null) {
                customAttributesRepository.setItemsForTag(tag, items).join()
            }
            if (newName != null && newName != tag) {
                customAttributesRepository.renameTag(tag, newName).join()
                searchableRepository.replace(Tag(tag).key, Tag(newName))
            }
        }
    }

    fun createTag(tag: String, items: List<SavableSearchable>) {
        scope.launch {
            customAttributesRepository.setItemsForTag(tag, items)
        }
    }

    fun getTags(it: SavableSearchable): Flow<List<String>> {
        return customAttributesRepository.getTags(it)
    }
}