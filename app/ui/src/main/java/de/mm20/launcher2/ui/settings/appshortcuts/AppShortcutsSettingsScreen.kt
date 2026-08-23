package de.mm20.launcher2.ui.settings.appshortcuts

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.ktx.toPixels

data object AppShortcutsSettingsRoute : NavKey

@Composable
fun AppShortcutsSettingsScreen() {

    val viewModel: AppShortcutSettingsScreenVM = viewModel()

    val searchEnabled by viewModel.isShortcutSearchEnabled.collectAsStateWithLifecycle(null)
    val hasPermission by viewModel.hasAppShortcutPermission.collectAsStateWithLifecycle(
        null
    )
    val apps by viewModel.apps.collectAsStateWithLifecycle(emptyList())
    val blocklist by viewModel.blocklist.collectAsStateWithLifecycle()

    val activity = LocalActivity.current
    val context = LocalContext.current

    val xsShape = MaterialTheme.shapes.extraSmall
    val mdShape = MaterialTheme.shapes.medium

    PreferenceScreen(
        title = { Text(stringResource(R.string.preference_search_appshortcuts)) },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Box(modifier = Modifier.padding(bottom = 10.dp)) {

                PreferenceCategory {
                    GuardedPreference(
                        locked = hasPermission == false,
                        onUnlock = {
                            viewModel.requestAppShortcutsPermission(activity as AppCompatActivity)
                        },
                        description = stringResource(
                            R.string.missing_permission_appshortcuts_search_settings,
                            stringResource(R.string.app_name),
                        ),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_search_appshortcuts),
                            summary = stringResource(R.string.preference_search_appshortcuts_summary),
                            icon = R.drawable.mobile_arrow_up_right_24px,
                            value = searchEnabled == true && hasPermission == true,
                            onValueChanged = {
                                viewModel.setShortcutSearchEnabled(it)
                            },
                            enabled = hasPermission == true,
                        )
                    }
                }
            }
        }
        itemsIndexed(apps) { index, app ->
            val key = "${app.componentName.packageName}:${app.user.getSerialNumber(context)}"
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
                        val icon by viewModel.getIcon(app, size.toPixels().toInt())
                            .collectAsStateWithLifecycle(null)
                        val badge by viewModel.getBadge(app)
                            .collectAsStateWithLifecycle(null)
                        ShapedLauncherIcon(size = size, icon = { icon }, badge = { badge })
                    },
                    title = app.label,
                    value = !blocklist.contains(key),
                    onValueChanged = {
                        viewModel.setAppBlocklisted(key, !it)
                    },
                )
            }
        }
    }
}