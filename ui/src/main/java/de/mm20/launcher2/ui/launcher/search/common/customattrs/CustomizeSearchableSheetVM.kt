package de.mm20.launcher2.ui.launcher.search.common.customattrs

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import de.mm20.launcher2.customattrs.CustomIcon
import de.mm20.launcher2.icons.CustomIconSuggestion
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CustomizeSearchableSheetVM(
    private val searchable: Searchable
) : KoinComponent {
    private val iconRepository: IconRepository by inject()

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
}