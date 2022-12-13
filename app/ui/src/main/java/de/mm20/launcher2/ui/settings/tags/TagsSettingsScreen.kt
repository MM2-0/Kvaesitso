package de.mm20.launcher2.ui.settings.tags

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun TagsSettingsScreen() {
    val viewModel: TagsSettingsScreenVM = viewModel()

    LaunchedEffect(null) {
        viewModel.update()
    }

    PreferenceScreen(
        title = "Tags",
        floatingActionButton = {
            FloatingActionButton(onClick = { /*TODO*/ }) {
                Icon(Icons.Rounded.Add, null)
            }
        }
    ) {
        item {
            PreferenceCategory {
                for (tag in viewModel.tags.value) {
                    Preference(
                        icon = Icons.Rounded.Tag,
                        title = tag,
                    )
                }
            }
        }
    }
}