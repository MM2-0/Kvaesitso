package de.mm20.launcher2.ui.settings.favorites

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object ExcludedFrequentlyUsedSettingsRoute : NavKey

@Composable
fun ExcludedFrequentlyUsedSettingsScreen() {
    val viewModel: ExcludedFrequentlyUsedSettingsScreenVM = viewModel()
    val density = LocalDensity.current
    val items by viewModel.excludedItems.collectAsState()

    PreferenceScreen(
        title = stringResource(R.string.preference_frequently_used_excluded),
    ) {
        val excluded = items ?: return@PreferenceScreen
        item {
            if (excluded.isEmpty()) {
                Text(
                    text = stringResource(R.string.preference_frequently_used_excluded_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                PreferenceCategory {
                    for (searchable in excluded) {
                        val icon by remember(searchable.key) {
                            viewModel.getIcon(searchable, with(density) { 32.dp.roundToPx() })
                        }.collectAsState(null)
                        Preference(
                            title = searchable.label,
                            icon = {
                                ShapedLauncherIcon(
                                    size = 32.dp,
                                    icon = { icon },
                                )
                            },
                            controls = {
                                IconButton(onClick = { viewModel.restore(searchable) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.visibility_24px),
                                        contentDescription = stringResource(R.string.menu_frequently_used_include),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
