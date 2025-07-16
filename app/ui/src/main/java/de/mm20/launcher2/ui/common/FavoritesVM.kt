package de.mm20.launcher2.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.preferences.search.FavoritesSettings
import de.mm20.launcher2.preferences.search.FavoritesSettingsData
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class FavoritesVM : ViewModel(), KoinComponent {

    private val favoritesService: FavoritesService by inject()
    internal val widgetRepository: WidgetRepository by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    internal val settings: FavoritesSettings by inject()

    val selectedTag = MutableStateFlow<String?>(null)

    val showEditButton =
        settings.showEditButton.stateIn(viewModelScope, SharingStarted.Lazily, false)
    abstract val tagsExpanded: Flow<Boolean>
    abstract val compactTags: Flow<Boolean>

    val pinnedTags = favoritesService.getFavorites(
        includeTypes = listOf("tag"),
        minPinnedLevel = PinnedLevel.AutomaticallySorted,
    ).map {
        it.filterIsInstance<Tag>()
    }

    open val favorites: Flow<List<SavableSearchable>> = selectedTag.flatMapLatest { tag ->
        if (tag == null) {
            val excludeCalendar = widgetRepository.exists(CalendarWidget.Type)

            combine(
                excludeCalendar,
                settings,
            ) { (a, b) -> a as Boolean to b as FavoritesSettingsData }
                .transformLatest {

                    val columns = it.second.columns
                    val excludeCalendar = it.first
                    val includeFrequentlyUsed = it.second.frequentlyUsed
                    val frequentlyUsedRows = it.second.frequentlyUsedRows

                    val pinned = favoritesService.getFavorites(
                        excludeTypes = if (excludeCalendar) listOf(
                            "calendar",
                            "tasks.org",
                            "tag",
                            "plugin.calendar"
                        ) else listOf("tag"),
                        minPinnedLevel = PinnedLevel.AutomaticallySorted,
                        limit = 10 * columns,
                    )
                    if (includeFrequentlyUsed) {
                        emitAll(pinned.flatMapLatest { pinned ->
                            favoritesService.getFavorites(
                                excludeTypes = if (excludeCalendar) listOf(
                                    "calendar",
                                    "tasks.org",
                                    "tag",
                                    "plugin.calendar"
                                ) else listOf("tag"),
                                maxPinnedLevel = PinnedLevel.FrequentlyUsed,
                                minPinnedLevel = PinnedLevel.FrequentlyUsed,
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
            customAttributesRepository
                .getItemsForTag(tag)
                .withCustomLabels(customAttributesRepository)
                .map { it.sortedBy { it } }
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)


    fun selectTag(tag: String?) {
        selectedTag.value = tag
    }

    abstract fun setTagsExpanded(expanded: Boolean)
}