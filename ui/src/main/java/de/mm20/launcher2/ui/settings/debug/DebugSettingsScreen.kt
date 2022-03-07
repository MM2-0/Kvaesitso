package de.mm20.launcher2.ui.settings.debug

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.debug.DebugInformationDumper
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DebugSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    PreferenceScreen(
        stringResource(R.string.preference_screen_debug)
    ) {
        item {
            Preference(
                title = stringResource(R.string.preference_crash_reporter),
                summary = stringResource(R.string.preference_crash_reporter_summary),
                onClick = {
                    navController?.navigate("settings/debug/crashreporter")
                })

            Preference(
                title = stringResource(R.string.preference_export_log),
                onClick = {
                    scope.launch {
                        val path = DebugInformationDumper().dump(context)
                        context.tryStartActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                            context,
                                            context.applicationContext.packageName + ".fileprovider",
                                            File(path)
                                        )
                                    )
                                }, null
                            )
                        )
                    }
                })
        }
    }
}