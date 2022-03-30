package de.mm20.launcher2.ui.base

import androidx.compose.runtime.*
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.component.ProvideIconShape
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridColumns
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.inject

@Composable
fun ProvideSettings(
    content: @Composable () -> Unit
) {
    val dataStore: LauncherDataStore by inject()
    val widgetRepository: WidgetRepository by inject()

    val cardStyle by remember {
        dataStore.data.map { it.cards }.distinctUntilChanged()
    }.collectAsState(
        Settings.CardSettings.getDefaultInstance()
    )
    val iconShape by remember {
        dataStore.data.map {
            if (it.easterEgg) Settings.IconSettings.IconShape.EasterEgg
            else it.icons.shape
        }.distinctUntilChanged()
    }.collectAsState(Settings.IconSettings.IconShape.Circle)

    val favoritesEnabled by remember {
        combine(
            widgetRepository.isFavoritesWidgetEnabled(),
            dataStore.data.map { it.favorites.enabled }
        ) { a, b -> a || b }.distinctUntilChanged()
    }.collectAsState(true)

    val gridColumns by remember {
        dataStore.data.map { it.grid.columnCount }.distinctUntilChanged()
    }.collectAsState(5)

    CompositionLocalProvider(
        LocalCardStyle provides cardStyle,
        LocalFavoritesEnabled provides favoritesEnabled,
        LocalGridColumns provides gridColumns,
    ) {
        ProvideIconShape(iconShape) {
            content()
        }
    }

}