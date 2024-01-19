package de.mm20.launcher2.ui.base

import androidx.compose.runtime.*
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.LegacySettings
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
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.inject

@Composable
fun ProvideSettings(
    content: @Composable () -> Unit
) {
    val settings: UiSettings by inject()
    val widgetRepository: WidgetRepository by inject()

    val cardStyle by remember {
        settings.cardStyle.distinctUntilChanged()
    }.collectAsState(
        CardStyle()
    )
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
        LocalCardStyle provides cardStyle,
        LocalFavoritesEnabled provides favoritesEnabled,
        LocalGridSettings provides gridSettings,
    ) {
        ProvideIconShape(iconShape) {
            content()
        }
    }

}