package de.mm20.launcher2.ui.launcher.sheets

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class CustomizeSearchableSheetVM(
    private val searchable: SavableSearchable
) : KoinComponent {
    private val iconRepository: IconRepository by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()

    val isIconPickerOpen = MutableLiveData(false)

    fun getIcon(size: Int): Flow<LauncherIcon> {
        return iconRepository.getIcon(searchable, size)
    }

    fun getIconSuggestions(size: Int) = liveData {
        emit(iconRepository.getCustomIconSuggestions(searchable, size))
    }

    fun openIconPicker() {
        isIconPickerOpen.value = true
    }

    fun closeIconPicker() {
        isIconPickerOpen.value = false
    }

    fun pickIcon(icon: CustomIcon?) {
        iconRepository.setCustomIcon(searchable, icon)
        closeIconPicker()
    }

    fun getDefaultIcon(size: Int) = liveData {
        emit(iconRepository.getUncustomizedDefaultIcon(searchable, size))
    }

    val iconSearchResults = MutableLiveData(emptyList<CustomIconWithPreview>())
    val isSearchingIcons = MutableLiveData(false)

    private var debounceSearchJob: Job? = null
    suspend fun searchIcon(query: String) {
        debounceSearchJob?.cancelAndJoin()
        if (query.isBlank()) {
            iconSearchResults.value = emptyList()
            isSearchingIcons.value = false
            return
        }
        withContext(coroutineContext) {
            debounceSearchJob = launch {
                delay(1000)
                isSearchingIcons.value = true
                iconSearchResults.value = iconRepository.searchCustomIcons(query)
                isSearchingIcons.value = false
            }
        }
    }

    fun setCustomLabel(label: String) {
        if (label.isBlank()) {
            customAttributesRepository.clearCustomLabel(searchable)
        } else {
            customAttributesRepository.setCustomLabel(searchable, label)
        }
    }

    fun setTags(tags: List<String>) {
        customAttributesRepository.setTags(searchable, tags)
    }

    fun getTags(): Flow<List<String>> {
        return customAttributesRepository.getTags(searchable)
    }

    suspend fun autocompleteTags(query: String): List<String> {
        return customAttributesRepository.getAllTags(startsWith = query)
    }
}