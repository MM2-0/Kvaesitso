package de.mm20.launcher2.ui.screens.settings

import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.accompanist.insets.statusBarsPadding
import de.mm20.launcher2.ui.R

@Composable
fun SettingsMainScreen() {
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(stringResource(id = R.string.title_activity_settings))
            },
            modifier = Modifier.statusBarsPadding()
        )
    }) {
    }
}