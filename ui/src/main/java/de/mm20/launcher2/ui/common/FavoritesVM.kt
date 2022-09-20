package de.mm20.launcher2.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

open class FavoritesVM : ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val widgetRepository: WidgetRepository by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val dataStore: LauncherDataStore by inject()

    val selectedTag = MutableStateFlow<String?>(null)

    val favorites: Flow<List<Searchable>> = selectedTag.flatMapLatest { tag ->
        if (tag == null) {
            val columns = dataStore.data.map { it.grid.columnCount }
            val excludeCalendar = widgetRepository.isCalendarWidgetEnabled()
            val favoritesEnabled = dataStore.data.map { it.favorites.enabled }
            val includeFrequentlyUsed = dataStore.data.map { it.favorites.frequentlyUsed }
            val frequentlyUsedRows = dataStore.data.map { it.favorites.frequentlyUsedRows }

            combine(
                listOf(
                    favoritesEnabled,
                    columns,
                    excludeCalendar,
                    includeFrequentlyUsed,
                    frequentlyUsedRows
                )
            ) { it }.transformLatest {

                val favoritesEnabled = it[0] as Boolean
                val columns = it[1] as Int
                val excludeCalendar = it[2] as Boolean
                val includeFrequentlyUsed = it[3] as Boolean
                val frequentlyUsedRows = it[4] as Int

                if (!favoritesEnabled) {
                    return@transformLatest
                }

                val pinned = favoritesRepository.getFavorites(
                    excludeTypes = if (excludeCalendar) listOf("calendar") else null,
                    manuallySorted = true,
                    automaticallySorted = true,
                    limit = 10 * columns,
                )
                if (includeFrequentlyUsed) {
                    emitAll(pinned.flatMapLatest { pinned ->
                        favoritesRepository.getFavorites(
                            excludeTypes = if (excludeCalendar) listOf("calendar") else null,
                            frequentlyUsed = true,
                            limit = frequentlyUsedRows * columns - pinned.size % columns,
                        ).map {
                            pinned + it
                        }
                    })
                } else {
                    emitAll(pinned)
                }
            }
        } else {
            emptyFlow<List<Searchable>>()
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)


    fun selectTag(tag: String?) {
        selectedTag.value = tag
    }
}