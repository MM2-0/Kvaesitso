package de.mm20.launcher2.ui.settings.hiddenitems

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun HiddenItemsSettingsScreen() {
    val viewModel: HiddenItemsSettingsScreenVM = viewModel()

    val context = LocalContext.current
    val density = LocalDensity.current

    val apps by viewModel.allApps.collectAsState()
    val other by viewModel.hiddenItems.collectAsState()

    val showButton by viewModel.hiddenItemsButton.collectAsState()

    PreferenceScreen(title = stringResource(R.string.preference_hidden_items)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_hidden_items_reveal_button),
                    summary = stringResource(R.string.preference_hidden_items_reveal_button_summary),
                    value = showButton,
                    onValueChanged = { viewModel.setHiddenItemsButton(it) }
                )
            }
        }
        items(apps, key = { it.key }) { searchable ->
            val icon by remember(searchable.key) {
                viewModel.getIcon(searchable, with(density) { 32.dp.roundToPx() })
            }.collectAsState(null)

            val isHidden by remember(searchable.key) {
                viewModel.isHidden(searchable)
            }.collectAsState(false)

            var showPopup by remember(searchable.key) {
                mutableStateOf(false)
            }

            Box {
                HiddenItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                viewModel.setHidden(searchable, !isHidden)
                            },
                            onLongClick = {
                                showPopup = true
                            }
                        ),
                    icon = icon,
                    label = searchable.label,
                    isHidden = isHidden,
                )

                DropdownMenu(
                    expanded = showPopup,
                    onDismissRequest = { showPopup = false },
                    offset = DpOffset(16.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_launch)) },
                        onClick = {
                            viewModel.launch(context, searchable)
                            showPopup = false
                        })

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_app_info)) },
                        onClick = {
                            viewModel.openAppInfo(context, searchable)
                            showPopup = false
                        })

                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isHidden) R.string.menu_unhide else R.string.menu_hide
                                )
                            )
                        },
                        onClick = {
                            viewModel.setHidden(searchable, !isHidden)
                            showPopup = false
                        })
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
            )
        }

        items(other, key = { it.key }) { searchable ->
            val icon by remember(searchable.key) {
                viewModel.getIcon(searchable, with(density) { 32.dp.roundToPx() })
            }.collectAsState(null)

            val isHidden by remember(searchable.key) {
                viewModel.isHidden(searchable)
            }.collectAsState(false)

            var showPopup by remember(searchable.key) {
                mutableStateOf(false)
            }

            Box {
                HiddenItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                viewModel.setHidden(searchable, !isHidden)
                            },
                            onLongClick = {
                                showPopup = true
                            }
                        ),
                    icon = icon,
                    label = searchable.label,
                    isHidden = isHidden,
                )

                DropdownMenu(
                    expanded = showPopup,
                    onDismissRequest = { showPopup = false },
                    offset = DpOffset(16.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_open_file)) },
                        onClick = {
                            viewModel.launch(context, searchable)
                            showPopup = false
                        })

                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isHidden) R.string.menu_unhide else R.string.menu_hide
                                )
                            )
                        },
                        onClick = {
                            viewModel.setHidden(searchable, !isHidden)
                            showPopup = false
                        })
                }
            }
        }
    }
}

@Composable
fun HiddenItem(
    modifier: Modifier,
    icon: LauncherIcon?,
    label: String,
    isHidden: Boolean,
) {
    Row(
        modifier = modifier
            .padding(vertical = 12.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShapedLauncherIcon(
            size = 32.dp,
            icon = { icon },
            modifier = Modifier.padding(end = 20.dp)
        )
        Text(
            label,
            modifier = Modifier.weight(1f, fill = true),
            style = MaterialTheme.typography.titleMedium
        )
        Icon(
            modifier = Modifier.alpha(if (isHidden) 0.3f else 1f),
            imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            tint = if (isHidden) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            contentDescription = null
        )
    }
}