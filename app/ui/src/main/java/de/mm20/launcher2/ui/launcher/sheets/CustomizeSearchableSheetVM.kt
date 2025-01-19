package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.mutableStateOf
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class CustomizeSearchableSheetVM(
    private val searchable: SavableSearchable
) : KoinComponent {
    private val iconService: IconService by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val favoritesService: FavoritesService by inject()

    val isIconPickerOpen = mutableStateOf(false)

    fun getIcon(size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }

    fun openIconPicker() {
        isIconPickerOpen.value = true
    }

    fun closeIconPicker() {
        isIconPickerOpen.value = false
    }

    fun pickIcon(icon: CustomIcon?) {
        iconService.setCustomIcon(searchable, icon)
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

    fun setVisibility(visibility: VisibilityLevel) {
        favoritesService.setVisibility(searchable, visibility)
    }

    fun getTags(): Flow<List<String>> {
        return customAttributesRepository.getTags(searchable)
    }

    fun getVisibility(): Flow<VisibilityLevel> {
        return favoritesService.getVisibility(searchable)
    }

    suspend fun autocompleteTags(query: String): List<String> {
        return customAttributesRepository.getAllTags(startsWith = query).first()
    }
}