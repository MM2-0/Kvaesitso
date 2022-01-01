package de.mm20.launcher2.ui.settings.about

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.icons.Fdroid
import de.mm20.launcher2.ui.icons.GitHub
import de.mm20.launcher2.ui.icons.Telegram
import de.mm20.launcher2.ui.locals.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen() {
    val viewModel: AboutScreenVM = viewModel()
    val navController = LocalNavController.current
    val context = LocalContext.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_about)) {
        item {
            PreferenceCategory {
                var appVersion by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(null) {
                    appVersion = withContext(Dispatchers.IO) {
                        context.packageManager.getPackageInfo(
                            context.packageName,
                            0
                        ).versionName
                    }
                }
                Preference(
                    title = stringResource(R.string.preference_version),
                    summary = appVersion
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
                    icon = Icons.Rounded.GitHub,
                    title = "GitHub",
                    summary = "github.com/MM2-0/Kvaesitso",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://github.com/MM2-0/Kvaesitso")
                        })
                    }
                )
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