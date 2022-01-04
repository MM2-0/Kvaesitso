package de.mm20.launcher2.ui.settings.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Today
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun WidgetsSettingsScreen() {
    val navController = LocalNavController.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_widgets)) {
        item {
            Preference(
                title = stringResource(R.string.preference_screen_weatherwidget),
                icon = Icons.Rounded.LightMode,
                onClick = {
                    navController?.navigate("settings/widgets/weather")
                }
            )
            Preference(
                title = stringResource(R.string.preference_screen_calendarwidget),
                icon = Icons.Rounded.Today
            )
        }
    }
}