package de.mm20.launcher2.ui.help

import de.mm20.launcher2.ui.component.TelosCyberHeading

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.preferences.ui.UiSettings
import org.koin.compose.koinInject

@Composable
fun HelpIntroHost() {
    val uiSettings = koinInject<UiSettings>()
    val shown by uiSettings.helpIntroShown.collectAsStateWithLifecycle(true)
    val context = LocalContext.current
    if (shown) return
    AlertDialog(
        onDismissRequest = { uiSettings.setHelpIntroShown(true) },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_help_intro_title))  } },
        text = { Text(stringResource(I18nR.string.telos_help_intro_body)) },
        confirmButton = {
            TextButton(onClick = {
                uiSettings.setHelpIntroShown(true)
                openHelpApp(context, HELP_TOPIC_LAUNCHER)
            }) {
                Text(stringResource(I18nR.string.telos_help_intro_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = { uiSettings.setHelpIntroShown(true) }) {
                Text(stringResource(I18nR.string.telos_help_intro_no))
            }
        },
    )
}
