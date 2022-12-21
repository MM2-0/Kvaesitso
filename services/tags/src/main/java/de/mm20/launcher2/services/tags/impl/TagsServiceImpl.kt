package de.mm20.launcher2.services.tags.impl

import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.Tag
import de.mm20.launcher2.services.tags.TagsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class TagsServiceImpl(
    private val customAttributesRepository: CustomAttributesRepository,
    private val favoritesRepository: FavoritesRepository,
) : TagsService {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    override fun getAllTags(startsWith: String?): Flow<List<String>> {
        return customAttributesRepository.getAllTags(startsWith)
    }

    override fun deleteTag(tag: String) {
        favoritesRepository.remove(Tag(tag))
        customAttributesRepository.deleteTag(tag)
    }

    override fun cloneTag(tag: String, newTag: String) {
        scope.launch {
            val items = getTaggedItems(tag).first()
            createTag(newTag, items)
        }
    }

    override fun getTaggedItems(tag: String): Flow<List<SavableSearchable>> {
        return customAttributesRepository.getItemsForTag(tag)
    }

    override fun updateTag(tag: String, newName: String?, items: List<SavableSearchable>?) {
        scope.launch {
            if (items != null) {
                customAttributesRepository.setItemsForTag(tag, items).join()
            }
            if (newName != null && newName != tag) {
                customAttributesRepository.renameTag(tag, newName).join()
                val pinnedTags = favoritesRepository.getFavorites(
                    includeTypes = listOf(Tag.Domain),
                    manuallySorted = true,
                    automaticallySorted = true
                ).first()
                val oldTag = Tag(tag)
                if (pinnedTags.any { it.key == oldTag.key }) {
                    favoritesRepository.unpinItem(oldTag)
                    favoritesRepository.pinItem(Tag(newName))
                }
            }

        }
    }

    override fun createTag(tag: String, items: List<SavableSearchable>) {
        scope.launch {
            customAttributesRepository.setItemsForTag(tag, items)
        }
    }
}