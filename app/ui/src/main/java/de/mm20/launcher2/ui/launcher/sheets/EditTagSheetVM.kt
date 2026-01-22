package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.tags.TagsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditTagSheetVM : ViewModel(), KoinComponent {

    private val tagService: TagsService by inject()
    private val searchService: SearchService by inject()
    private val iconService: IconService by inject()
    private val appRepository: AppRepository by inject()

    private var oldTagName by mutableStateOf<String?>(null)
    private var allTags by mutableStateOf(emptySet<String>())
    var tagName by mutableStateOf("")
    var tagCustomIcon = MutableStateFlow<CustomIcon?>(null)
    var tagIcon = emptyFlow<LauncherIcon?>()

    var loading by mutableStateOf(true)

    var page by mutableStateOf(EditTagSheetPage.CreateTag)
    var wasOnLastPage by mutableStateOf(false)

    var taggedItems by mutableStateOf(emptyList<SavableSearchable>())
    var taggableApps by mutableStateOf(emptyList<TaggableItem>())
    var taggableOther by mutableStateOf(emptyList<TaggableItem>())

    val tagNameExists by derivedStateOf {
        tagName != oldTagName && allTags.contains(tagName)
    }


    fun init(tag: String?, iconSize: Int) {
        loading = true
        this.oldTagName = tag
        this.tagName = tag ?: ""
        this.page = if (tag == null) EditTagSheetPage.CreateTag else EditTagSheetPage.CustomizeTag
        this.wasOnLastPage = this.page == EditTagSheetPage.CustomizeTag
        this.taggedItems = emptyList()
        viewModelScope.launch(Dispatchers.Default) {
            allTags = tagService.getAllTags().first().toSet()
            val items = if (tag != null) tagService.getTaggedItems(tag).first() else emptyList()
            tagCustomIcon.value = if (tag != null) iconService.getCustomIcon(Tag(tag)).first() else null
            tagIcon = tagCustomIcon.map {
                if (tag != null) iconService.resolveCustomIcon(Tag(tag), iconSize, it).first()
                else null
            }

            val apps = appRepository.findMany().first { it.isNotEmpty() }.sorted()
            taggedItems = items
            taggableApps = apps.map { app -> TaggableItem(app, items.any { app.key == it.key }) }
            taggableOther = items.mapNotNull { item ->
                if (apps.any { item.key == it.key }) null
                else TaggableItem(item, true)
            }.sortedBy { it.item }
            loading = false
        }
    }

    fun save() {
        val oldName = oldTagName
        val newName = tagName
        val tagIcon = tagCustomIcon.value
        if ((taggedItems.isEmpty() || tagName.isEmpty()) && oldName != null) tagService.deleteTag(oldName)
        else if (oldName != null) tagService.updateTag(oldName, newName = newName, items = taggedItems)
        else tagService.createTag(tagName, taggedItems)

        if (oldName != null && oldName != newName) {
            iconService.setCustomIcon(Tag(oldName), null)
        }
        if (tagIcon != null) {
            iconService.setCustomIcon(Tag(newName), tagIcon)
        } else {
            iconService.setCustomIcon(Tag(newName), null)
        }
    }

    fun onClickContinue() {
        if (page == EditTagSheetPage.CreateTag && tagNameExists) return
        page = if (page == EditTagSheetPage.CreateTag) EditTagSheetPage.PickItems else EditTagSheetPage.CustomizeTag
        if (page == EditTagSheetPage.CustomizeTag) {
            wasOnLastPage = true
        }
        oldTagName = tagName
    }

    fun getIcon(item: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(item, size)
    }

    fun openItemPicker() {
        page = EditTagSheetPage.PickItems
    }

    fun openIconPicker() {
        page = EditTagSheetPage.PickIcon
    }

    fun closeIconPicker() {
        page = EditTagSheetPage.CustomizeTag
    }

    fun selectIcon(icon: CustomIcon?) {
        tagCustomIcon.value = icon
        closeIconPicker()
    }

    fun closeItemPicker() {
        page = EditTagSheetPage.CustomizeTag
    }

    fun tagItem(item: SavableSearchable) {
        taggedItems = taggedItems + item
        taggableApps =
            taggableApps.map { app -> app.copy(isTagged = taggedItems.any { it.key == app.item.key }) }
        taggableOther =
            taggableOther.map { oth -> oth.copy(isTagged = taggedItems.any { it.key == oth.item.key }) }
    }

    fun untagItem(item: SavableSearchable) {
        taggedItems = taggedItems.filter { it.key != item.key }
        taggableApps =
            taggableApps.map { app -> app.copy(isTagged = taggedItems.any { it.key == app.item.key }) }
        taggableOther =
            taggableOther.map { oth -> oth.copy(isTagged = taggedItems.any { it.key == oth.item.key }) }
    }
}

enum class EditTagSheetPage {
    CreateTag,
    PickItems,
    CustomizeTag,
    PickIcon,
}

@Stable
data class TaggableItem(val item: SavableSearchable, val isTagged: Boolean)