package de.mm20.launcher2.ui.settings.debug

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController
import kotlinx.coroutines.launch

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
                    title = stringResource(R.string.preference_logs),
                    summary = stringResource(R.string.preference_logs_summary),
                    onClick = {
                        navController?.navigate("settings/debug/logs")
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
                Preference(
                    title = stringResource(R.string.preference_debug_reinstall_iconpacks),
                    summary = stringResource(R.string.preference_debug_reinstall_iconpacks_summary),
                    onClick = {
                        viewModel.reinstallIconPacks()
                    })
            }
        }
    }
}