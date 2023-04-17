package de.mm20.launcher2.ui.settings.debug

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.searchable.SearchableRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DebugSettingsScreenVM: ViewModel(), KoinComponent {

    private val searchableRepository: SearchableRepository by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val iconService: IconService by inject()
    suspend fun cleanUpDatabase(): Int {
        var removed = searchableRepository.cleanupDatabase()
        removed += customAttributesRepository.cleanupDatabase()
        return removed
    }

    fun reinstallIconPacks() {
        iconService.reinstallAllIconPacks()
    }
}