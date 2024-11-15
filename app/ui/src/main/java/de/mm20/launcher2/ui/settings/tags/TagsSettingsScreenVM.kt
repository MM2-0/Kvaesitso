package de.mm20.launcher2.ui.settings.tags

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.tags.TagsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TagsSettingsScreenVM: ViewModel(), KoinComponent {
    private val tagsService: TagsService by inject()
    private val iconService: IconService by inject()

    val tags = tagsService.getAllTags()

    var editTag = mutableStateOf<String?>(null)
    var createTag = mutableStateOf(false)

    fun duplicateTag(tag: String) {
        viewModelScope.launch {
            val allTags = tags.first()
            var i = 2
            var newName = "$tag ($i)"
            while(allTags.contains(newName)) {
                i++
                newName = "$tag ($i)"
            }
            tagsService.cloneTag(tag, newName)
        }
    }

    fun deleteTag(tag: String) {
        tagsService.deleteTag(tag)
    }

    fun getIcon(tag: String): Flow<LauncherIcon?> {
        return iconService.getIcon(Tag(tag), 1)
    }

}