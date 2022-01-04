package de.mm20.launcher2.ui.settings.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun DebugSettingsScreen() {
    val context = LocalContext.current
    PreferenceScreen(
        stringResource(R.string.preference_screen_debug)
    ) {
        item {
            Preference(
                title = stringResource(R.string.preference_crash_reporter),
                summary = stringResource(R.string.preference_crash_reporter_summary),
                onClick = {
                    context.startActivity(CrashReporter.getLaunchIntent())
                })
        }
    }
}