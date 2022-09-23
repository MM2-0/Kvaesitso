package de.mm20.launcher2.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.search.data.Tag
import de.mm20.launcher2.ui.utils.withCustomLabels
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

    val pinnedTags = favoritesRepository.getFavorites(
        includeTypes = listOf("tag"),
        manuallySorted = true,
        automaticallySorted = true,
    ).map {
        it.filterIsInstance<Tag>()
    }

    val favorites: Flow<List<Searchable>> = selectedTag.flatMapLatest { tag ->
        if (tag == null) {
            val columns = dataStore.data.map { it.grid.columnCount }
            val excludeCalendar = widgetRepository.isCalendarWidgetEnabled()
            val includeFrequentlyUsed = dataStore.data.map { it.favorites.frequentlyUsed }
            val frequentlyUsedRows = dataStore.data.map { it.favorites.frequentlyUsedRows }

            combine(
                listOf(
                    columns,
                    excludeCalendar,
                    includeFrequentlyUsed,
                    frequentlyUsedRows
                )
            ) { it }.transformLatest {

                val columns = it[0] as Int
                val excludeCalendar = it[1] as Boolean
                val includeFrequentlyUsed = it[2] as Boolean
                val frequentlyUsedRows = it[3] as Int

                val pinned = favoritesRepository.getFavorites(
                    excludeTypes = if (excludeCalendar) listOf("calendar", "tag") else listOf("tag"),
                    manuallySorted = true,
                    automaticallySorted = true,
                    limit = 10 * columns,
                )
                if (includeFrequentlyUsed) {
                    emitAll(pinned.flatMapLatest { pinned ->
                        favoritesRepository.getFavorites(
                            excludeTypes = if (excludeCalendar) listOf("calendar", "tag") else listOf("tag"),
                            frequentlyUsed = true,
                            limit = frequentlyUsedRows * columns - pinned.size % columns,
                        ).map {
                            pinned + it
                        }
                            .withCustomLabels(customAttributesRepository)
                    })
                } else {
                    emitAll(
                        pinned.withCustomLabels(customAttributesRepository)
                    )
                }
            }
        } else {
            customAttributesRepository.getItemsForTag(tag)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)


    fun selectTag(tag: String?) {
        selectedTag.value = tag
    }
}