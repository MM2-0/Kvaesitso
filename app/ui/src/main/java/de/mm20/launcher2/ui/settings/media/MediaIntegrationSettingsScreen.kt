package de.mm20.launcher2.ui.settings.media

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun MediaIntegrationSettingsScreen() {
    val context = LocalContext.current
    val viewModel: MediaIntegrationSettingsScreenVM = viewModel()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle(null)
    val loading by viewModel.loading

    val density = LocalDensity.current

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(null) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume(density = density.density)
        }
    }

    PreferenceScreen(
        stringResource(R.string.preference_media_integration),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/integrations/mediacontrol"
    ) {
        if (loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (hasPermission == false) {
            item {
                MissingPermissionBanner(
                    text = stringResource(R.string.missing_permission_music_widget),
                    onClick = {
                        viewModel.requestNotificationPermission(context as AppCompatActivity)
                    },
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium
                        )
                        .padding(16.dp)
                )
            }
        }
        item {
            PreferenceCategory(
                stringResource(R.string.preference_category_media_apps)
            ) {
                val apps by viewModel.appList
                for (app in apps) {
                    val icon by app.icon.collectAsState(null)
                    CheckboxPreference(
                        icon = {
                            ShapedLauncherIcon(size = 32.dp, icon = { icon })
                        },
                        title = app.label,
                        value = app.isChecked,
                        onValueChanged = {
                            viewModel.onAppChecked(app, it)
                        }
                    )
                }
            }
        }

        if (BuildConfig.DEBUG) {
            item {
                PreferenceCategory(stringResource(R.string.preference_category_debug)) {
                    Preference(
                        title = "Reset widget",
                        summary = "Clear all music data",
                        onClick = {
                            viewModel.resetWidget()
                        }
                    )
                }
            }
        }
    }
}