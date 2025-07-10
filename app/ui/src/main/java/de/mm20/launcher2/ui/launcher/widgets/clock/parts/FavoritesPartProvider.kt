package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FavoritesPartProvider : PartProvider, KoinComponent {

    private val favoritesService: FavoritesService by inject()
    private val widgetRepository: WidgetRepository by inject()
    private val uiSettings: UiSettings by inject()

    override fun getRanking(context: Context): Flow<Int> = flow {
        emit(Int.MAX_VALUE)
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val columns by remember {
            uiSettings.gridSettings.map {
                it.columnCount
            }
        }.collectAsState(0)
        val dockRows by uiSettings.dockRows.collectAsState(1)
        val excludeCalendar by remember { widgetRepository.exists(CalendarWidget.Type) }.collectAsState(
            true
        )

        val favorites by remember(columns, dockRows, excludeCalendar) {
            favoritesService.getFavorites(
                excludeTypes = if (excludeCalendar) listOf("calendar", "tag") else listOf("tag"),
                minPinnedLevel = PinnedLevel.FrequentlyUsed,
                limit = columns * dockRows
            )
        }.collectAsState(emptyList())


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            SearchResultGrid(
                items = favorites,
                showLabels = false,
                columns = columns.coerceAtMost(favorites.size),
                transitionKey = null,
            )
        }
    }
}