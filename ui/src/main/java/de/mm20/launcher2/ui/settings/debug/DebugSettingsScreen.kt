package de.mm20.launcher2.ui.settings.debug

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.debug.DebugInformationDumper
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DebugSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: DebugSettingsScreenVM = viewModel()
    val navController = LocalNavController.current
    PreferenceScreen(
        stringResource(R.string.preference_screen_debug)
    ) {
        item {
            PreferenceCategory {
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
            PreferenceCategory(stringResource(R.string.preference_category_debug_tools)) {
                Preference(
                    title = stringResource(R.string.preference_debug_cleanup_database),
                    summary = stringResource(R.string.preference_debug_cleanup_database_summary),
                    onClick = {
                        scope.launch {
                            val removedCount = viewModel.cleanUpDatabase()
                            Toast.makeText(
                                context,
                                context.resources.getQuantityString(
                                    R.plurals.debug_cleanup_database_result,
                                    removedCount,
                                    removedCount
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })
            }
        }
    }
}