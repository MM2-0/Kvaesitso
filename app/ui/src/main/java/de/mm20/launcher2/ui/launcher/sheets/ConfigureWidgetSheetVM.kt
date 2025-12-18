package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.widgets.FavoritesWidget
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.Collator

class ConfigureWidgetSheetVM : ViewModel(), KoinComponent {

    private val customAttributesRepository: CustomAttributesRepository by inject()

    val availableTags = mutableStateOf<List<Tag>>(emptyList())
    val selectedTags = mutableStateOf<List<String>>(emptyList())

    suspend fun reload(widget: FavoritesWidget) {

        val configTags = widget.config.tagList

        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

        selectedTags.value = configTags
        availableTags.value =
            customAttributesRepository
                .getAllTags()
                .first()
                .filter {t -> configTags.none { it == t } }
                .sortedWith { el1, el2 ->
                    collator.compare(el1, el2)
                }
                .map { Tag(it) }
    }

    fun addTag(tag: Tag)
    {
        selectedTags.value = (selectedTags.value + tag.tag)
        availableTags.value = availableTags.value.filter { it.tag != tag.tag }
    }

    fun clearTag(tag: Tag)
    {
        availableTags.value = (availableTags.value + tag)
        selectedTags.value = (selectedTags.value - tag.tag)
    }

    fun clearAllTags()
    {
        selectedTags.value.map{ availableTags.value += Tag(it) }
        selectedTags.value = emptyList()
    }

    fun addAvailableTag(tag: Tag)
    {
        availableTags.value = (availableTags.value + tag)
    }

    fun moveItem(from: LazyListItemInfo, to: LazyListItemInfo)
    {
        val newTagList = selectedTags.value.toMutableList()
        val tag = newTagList.removeAt(from.index)
        newTagList.add(to.index, tag)

        selectedTags.value = newTagList
    }
}