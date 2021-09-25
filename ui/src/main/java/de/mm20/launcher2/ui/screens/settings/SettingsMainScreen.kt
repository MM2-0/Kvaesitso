package de.mm20.launcher2.ui.screens.settings

import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.accompanist.insets.statusBarsPadding
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun SettingsMainScreen() {
    val navController = LocalNavController.current
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(stringResource(id = R.string.title_activity_settings))
            },
            modifier = Modifier.statusBarsPadding(),
            navigationIcon = {
                IconButton(onClick = {
                    navController?.navigateUp()
                }) {
                    Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) {
    }
}