package de.mm20.launcher2.ui.settings.locale

import android.app.LocaleConfig
import android.content.Intent
import android.content.pm.PackageManager
import android.os.LocaleList
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data object LocaleSettingsRoute: NavKey

@Composable
fun LocaleSettingsScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current

    val language = remember {
        AppCompatDelegate.getApplicationLocales().get(0)
    }


    PreferenceScreen(
        title = stringResource(R.string.preference_screen_locale)
    ) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_language),
                    summary = if (language == null) stringResource(R.string.preference_value_system_default) else language.getDisplayName(language),
                    enabled = isAtLeastApiLevel(33),
                    onClick = {
                        context.tryStartActivity(
                            Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                setData("package:${context.packageName}".toUri())
                            }
                        )
                    }
                )
            }
        }
    }
}