package de.mm20.launcher2.ui.settings.musicwidget

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun MusicWidgetSettingsScreen() {
    val context = LocalContext.current
    val viewModel: MusicWidgetSettingsScreenVM = viewModel()
    val hasPermission by viewModel.hasPermission.observeAsState()
    PreferenceScreen(
        stringResource(R.string.preference_screen_musicwidget)
    ) {
        item {
            PreferenceCategory {
                AnimatedVisibility(hasPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_music_widget),
                        onClick = {
                            viewModel.requestNotificationPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val filterSources by viewModel.filterSources.observeAsState(false)
                SwitchPreference(
                    title = stringResource(R.string.preference_music_filter_sources),
                    summary = stringResource(R.string.preference_music_filter_sources_summary),
                    value = filterSources,
                    onValueChanged = {
                        viewModel.setFilterSources(it)
                    }
                )
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