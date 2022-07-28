package de.mm20.launcher2.ui.launcher.search.common.customattrs

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import de.mm20.launcher2.customattrs.CustomIcon
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.flow.Flow
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

    fun getAllIconsFromAllIconPacks() = liveData {
        emit(emptyList())
        val iconPacks = iconRepository.getInstalledIconPacks()

        emit(iconPacks.map {
            val source = iconRepository.getAllIconsFromPack(it.packageName)

            Pager(
                PagingConfig(pageSize = 20, enablePlaceholders = false, maxSize = 200),
            ) {
                source
            }
        })

    }
}