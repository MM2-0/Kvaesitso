package de.mm20.launcher2.ui.settings.about

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsRoute
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsRoute
import de.mm20.launcher2.ui.settings.license.LicenseRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object AboutSettingsRoute: NavKey

@Composable
fun AboutSettingsScreen() {
    val viewModel: AboutSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
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
                var easterEggCounter by remember { mutableStateOf(0) }
                Preference(
                    title = stringResource(R.string.preference_version),
                    summary = appVersion,
                    onClick = {
                        easterEggCounter++
                        if (easterEggCounter >= 9) {
                            backStack.add(EasterEggSettingsRoute)
                            easterEggCounter = 0
                        }
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_screen_buildinfo),
                    summary = stringResource(R.string.preference_screen_buildinfo_summary),
                    onClick = {
                        backStack.add(BuildInfoSettingsRoute)
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_license)) {
                Preference(
                    icon = R.drawable.info_24px,
                    title = stringResource(id = R.string.preference_about_license),
                    summary = stringResource(id = R.string.preference_about_license_summary),
                    onClick = {
                        backStack.add(LicenseRoute())
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_links)) {
                Preference(
                    icon = R.drawable.github,
                    title = "GitHub",
                    summary = "github.com/MM2-0/Kvaesitso",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://github.com/MM2-0/Kvaesitso")
                        })
                    }
                )
                Preference(
                    icon = R.drawable.telegram,
                    title = stringResource(id = R.string.preference_about_telegram),
                    summary = "t.me/Kvaesitso",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://t.me/Kvaesitso")
                        })
                    }
                )
                Preference(
                    icon = R.drawable.fdroid,
                    title = stringResource(id = R.string.preference_about_fdroid),
                    summary = "fdroid.mm20.de",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data =
                                Uri.parse("https://fdroid.mm20.de")
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
                            backStack.add(LicenseRoute(libraryName = library.name))
                        }
                    )
                }
            }
        }
    }
}