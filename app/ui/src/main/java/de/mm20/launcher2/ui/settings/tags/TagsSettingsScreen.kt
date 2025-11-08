package de.mm20.launcher2.ui.settings.tags

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.launcher.sheets.EditTagSheet

@Composable
fun TagsSettingsScreen() {
    val viewModel: TagsSettingsScreenVM = viewModel()

    val tags by remember { viewModel.tags }.collectAsState(emptyList())

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_tags),
        topBarActions = {
            IconButton(onClick = { viewModel.createTag.value = true }) {
                Icon(painterResource(R.drawable.add_24px), stringResource(R.string.edit_favorites_dialog_new_tag))
            }
        },
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/concepts/tags"
    ) {
        item {
            PreferenceCategory {
                for (tag in tags) {
                    var showMenu by remember { mutableStateOf(false) }

                    val icon by remember(tag) { viewModel.getIcon(tag) }.collectAsState(null)

                    Preference(
                        icon = {
                            ShapedLauncherIcon(
                                size = 36.dp,
                                icon = { icon },
                            )
                        },
                        title = { Text(tag) },
                        onClick = {
                            viewModel.editTag.value = tag
                        },
                        controls = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(painterResource(R.drawable.more_vert_24px), null)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.content_copy_24px), null) },
                                    onClick = {
                                        viewModel.duplicateTag(tag)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_delete)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.delete_24px), null) },
                                    onClick = {
                                        viewModel.deleteTag(tag)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
    if (viewModel.editTag.value != null) {
        EditTagSheet(
            tag = viewModel.editTag.value,
            onDismiss = {
                viewModel.editTag.value = null
                viewModel.createTag.value = false
            }
        )
    } else if (viewModel.createTag.value) {
        EditTagSheet(
            tag = null,
            onDismiss = {
                viewModel.createTag.value = false
                viewModel.editTag.value = null
            }
        )
    }
}