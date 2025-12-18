package de.mm20.launcher2.ui.settings.debug

import android.content.Intent
import android.os.Debug
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterRoute
import de.mm20.launcher2.ui.settings.log.LogRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Serializable
data object DebugSettingsRoute: NavKey

@Composable
fun DebugSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dumpingHeap by remember { mutableStateOf(false) }
    val viewModel: DebugSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    PreferenceScreen(
        stringResource(R.string.preference_screen_debug)
    ) {
        if (dumpingHeap) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_crash_reporter),
                    summary = stringResource(R.string.preference_crash_reporter_summary),
                    onClick = {
                        backStack.add(CrashReporterRoute)
                    })

                Preference(
                    title = stringResource(R.string.preference_logs),
                    summary = stringResource(R.string.preference_logs_summary),
                    onClick = {
                        backStack.add(LogRoute)
                    })
                Preference(
                    title = stringResource(R.string.preference_debug_dump_heap),
                    summary = if (dumpingHeap) stringResource(R.string.preference_debug_dump_heap_in_progress)
                    else stringResource(R.string.preference_debug_dump_heap_summary),
                    enabled = !dumpingHeap,
                    onClick = {
                        scope.launch {
                            dumpingHeap = true
                            val df = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
                            val path = File(
                                context.externalCacheDir,
                                "kvaesitso-dump-${df.format(Date(System.currentTimeMillis()))}.hprof"
                            ).absolutePath
                            delay(100)
                            withContext(Dispatchers.Default) {
                                Debug.dumpHprofData(path)
                            }
                            dumpingHeap = false
                            context.tryStartActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
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

                    }
                )
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