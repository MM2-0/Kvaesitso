package de.mm20.launcher2.ui.settings.media

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.serialization.Serializable

@Serializable
data object MediaIntegrationSettingsRoute : NavKey

@Composable
fun MediaIntegrationSettingsScreen() {
    val context = LocalContext.current
    val viewModel: MediaIntegrationSettingsScreenVM = viewModel()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle(null)
    val apps by viewModel.appList

    val loading by viewModel.loading

    val density = LocalDensity.current
    val xsShape = MaterialTheme.shapes.extraSmall
    val mdShape = MaterialTheme.shapes.medium

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(null) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume(density = density.density)
        }
    }

    PreferenceScreen(
        stringResource(R.string.preference_media_integration),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/integrations/mediacontrol",
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                        .padding(bottom = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium
                        )
                        .padding(16.dp)
                )
            }
        }
        itemsIndexed(apps) { index, app ->
            Box(
                modifier = Modifier.clip(
                    when {
                        apps.size == 1 -> mdShape
                        index == 0 -> mdShape.copy(
                            bottomEnd = xsShape.bottomEnd,
                            bottomStart = xsShape.bottomStart
                        )

                        index == apps.size - 1 -> mdShape.copy(
                            topEnd = xsShape.topEnd,
                            topStart = xsShape.topStart
                        )

                        else -> xsShape
                    }
                )
            ) {
                CheckboxPreference(
                    icon = {
                        val size = 32.dp
                        val icon by viewModel.getIcon(app.app, size.toPixels().toInt())
                            .collectAsStateWithLifecycle(null)
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

        if (BuildConfig.DEBUG) {
            item {
                Box(
                    modifier = Modifier.padding(top = 10.dp)
                ) {
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
}