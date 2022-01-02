package de.mm20.launcher2.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun SettingsBadgesScreen() {
    val scope = rememberCoroutineScope()
    PreferenceScreen(
        title = stringResource(id = R.string.preference_screen_badges)
    ) {

    }
}