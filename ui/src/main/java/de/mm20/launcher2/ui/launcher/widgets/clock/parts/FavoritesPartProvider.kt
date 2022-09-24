package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FavoritesPartProvider : PartProvider, KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val widgetRepository: WidgetRepository by inject()
    private val dataStore: LauncherDataStore by inject()

    override fun getRanking(context: Context): Flow<Int> = flow {
        emit(2)
    }

    @Composable
    override fun Component(layout: ClockWidgetLayout) {
        val columns by remember(layout) {
            dataStore.data.map {
                val c = it.grid.columnCount
                if (layout == ClockWidgetLayout.Horizontal) c - 2 else c
            }
        }.collectAsState(0)
        val excludeCalendar by remember { widgetRepository.isCalendarWidgetEnabled() }.collectAsState(
            true
        )

        val favorites by remember(columns, excludeCalendar, layout) {
            favoritesRepository.getFavorites(
                excludeTypes = if (excludeCalendar) listOf("calendar", "tag") else listOf("tag"),
                manuallySorted = true,
                automaticallySorted = true,
                frequentlyUsed = true,
                limit = columns
            )
        }.collectAsState(emptyList())


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .wrapContentHeight()
        ) {
            SearchResultGrid(
                items = favorites, showLabels = false, columns = columns,
            )

        }
    }
}