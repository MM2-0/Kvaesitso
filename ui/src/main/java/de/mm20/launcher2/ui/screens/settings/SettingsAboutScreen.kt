package de.mm20.launcher2.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.debug.DebugInformationDumper
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.icons.Fdroid
import de.mm20.launcher2.ui.icons.GitHub
import de.mm20.launcher2.ui.icons.Telegram
import de.mm20.launcher2.ui.locals.LocalNavController
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutScreen() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    PreferenceScreen(
        title = stringResource(id = R.string.preference_screen_about),
    ) {
        item {
            PreferenceCategory {
                val version = context.packageManager.getPackageInfo(
                    context.packageName,
                    0
                ).versionName
                Preference(
                    title = stringResource(id = R.string.preference_version),
                    summary = version
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_license)) {
                Preference(
                    icon = Icons.Rounded.Info,
                    title = stringResource(id = R.string.preference_about_license),
                    summary = stringResource(id = R.string.preference_about_license_summary),
                    onClick = {
                        navController?.navigate("settings/license")
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_links)) {
                Preference(
                    icon = Icons.Rounded.Telegram,
                    title = stringResource(id = R.string.preference_about_telegram),
                    summary = "t.me/Kvaesitso",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://t.me/Kvaesitso")
                        })
                    }
                )
                Preference(
                    icon = Icons.Rounded.Fdroid,
                    title = stringResource(id = R.string.preference_about_fdroid),
                    summary = "github.com/MM2-0/fdroid",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data =
                                Uri.parse("https://raw.githubusercontent.com/MM2-0/fdroid/master/fdroid/repo")
                        })
                    }
                )
                Preference(
                    icon = Icons.Rounded.GitHub,
                    title = "GitHub",
                    summary = "github.com/MM2-0/Kvaesitso",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://github.com/MM2-0/Kvaesitso")
                        })
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_debug)) {
                Preference(
                    title = stringResource(id = R.string.preference_crash_reporter),
                    onClick = {
                        context.startActivity(CrashReporter.getLaunchIntent())
                    }
                )
                Preference(
                    title = stringResource(id = R.string.preference_export_debug),
                    onClick = {
                        scope.launch {
                            val path = DebugInformationDumper().dump(context)
                            /*val result = scaffoldState.snackbarHostState.showSnackbar(
                                context.getString(R.string.debug_export_information_file, path),
                                actionLabel = context.getString(R.string.menu_share),
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                context.tryStartActivity(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                            context,
                                            context.applicationContext.packageName + ".fileprovider",
                                            File(path)
                                        )
                                    )
                                })
                            }*/
                        }
                    }
                )
                Preference(
                    title = stringResource(id = R.string.preference_export_databases),
                    onClick = {
                        scope.launch {
                            val path = DebugInformationDumper().exportDatabases(context)
                            /*val result = scaffoldState.snackbarHostState.showSnackbar(
                                context.getString(R.string.debug_export_information_file, path),
                                actionLabel = context.getString(R.string.menu_share),
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                context.tryStartActivity(Intent(Intent.ACTION_SEND).apply {
                                    type = "application/x-sqlite3"
                                    putExtra(
                                        Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                            context,
                                            context.applicationContext.packageName + ".fileprovider",
                                            File(path)
                                        )
                                    )
                                })
                            }*/
                        }
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_licenses)) {
                for (library in OpenSourceLicenses.sortedBy { it.name.lowercase() }) {
                    Preference(
                        title = library.name,
                        summary = library.description,
                        onClick = {
                            navController?.navigate("settings/license?library=${library.name}")
                        }
                    )
                }
            }
        }
    }
}