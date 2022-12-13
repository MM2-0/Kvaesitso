package de.mm20.launcher2.ui.settings.tags

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TagsSettingsScreenVM: ViewModel(), KoinComponent {
    private val customAttributesRepository: CustomAttributesRepository by inject()

    val tags = mutableStateOf(emptyList<String>())

    suspend fun update() {
        tags.value = customAttributesRepository.getAllTags()
    }
    
}