package de.mm20.launcher2.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.ui.CardStyle
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.component.ProvideIconShape
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.widgets.FavoritesWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject

@Composable
fun ProvideSettings(
    content: @Composable () -> Unit
) {
    val settings: UiSettings = koinInject()
    val widgetRepository: WidgetRepository = koinInject()

    val iconShape by remember {
        settings.iconShape.distinctUntilChanged()
    }.collectAsState(IconShape.Circle)

    val favoritesEnabled by remember {
        combine(
            widgetRepository.exists(FavoritesWidget.Type),
            settings.favoritesEnabled,
        ) { a, b -> a || b }.distinctUntilChanged()
    }.collectAsState(true)

    val gridSettings by remember {
        settings.gridSettings.distinctUntilChanged()
    }.collectAsState(GridSettings())

    CompositionLocalProvider(
        LocalFavoritesEnabled provides favoritesEnabled,
        LocalGridSettings provides gridSettings,
    ) {
        ProvideIconShape(iconShape) {
            content()
        }
    }

}