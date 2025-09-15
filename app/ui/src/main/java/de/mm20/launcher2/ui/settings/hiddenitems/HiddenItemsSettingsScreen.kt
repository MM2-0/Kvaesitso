package de.mm20.launcher2.ui.settings.hiddenitems

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun HiddenItemsSettingsScreen() {
    val viewModel: HiddenItemsSettingsScreenVM = viewModel()

    val density = LocalDensity.current

    val apps by viewModel.allApps.collectAsState()
    val other by viewModel.hiddenItems.collectAsState()

    val showButton by viewModel.hiddenItemsButton.collectAsState()

    PreferenceScreen(
        title = stringResource(R.string.preference_hidden_items),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
        itemsIndexed(apps, key = { i, it -> it.key }) { i, searchable ->
            val icon by remember(searchable.key) {
                viewModel.getIcon(searchable, with(density) { 32.dp.roundToPx() })
            }.collectAsState(null)

            val visibility by remember(searchable.key) {
                viewModel.getVisibility(searchable)
            }.collectAsState(null)

            var showDropdown by remember { mutableStateOf(false) }

            val xs = MaterialTheme.shapes.extraSmall
            val md = MaterialTheme.shapes.medium

            Box(
                modifier = Modifier
                    .clip(
                        xs.copy(
                            topStart = if (i == 0) md.topStart else xs.topStart,
                            topEnd = if (i == 0) md.topEnd else xs.topEnd,
                            bottomStart = if (i == apps.lastIndex) md.bottomStart else xs.bottomStart,
                            bottomEnd = if (i == apps.lastIndex) md.bottomEnd else xs.bottomEnd
                        )
                    )
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                HiddenItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (searchable is Application || searchable is CalendarEvent) {
                                showDropdown = true
                            } else {
                                if (visibility == null) return@clickable
                                viewModel.setVisibility(
                                    searchable,
                                    if (visibility == VisibilityLevel.Default) VisibilityLevel.Hidden else VisibilityLevel.Default
                                )
                            }
                        },
                    icon = icon,
                    label = searchable.label,
                    visibility = visibility,
                )
                VisibilityDropdown(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    item = searchable,
                    value = visibility,
                    onValueChanged = {
                        viewModel.setVisibility(searchable, it)
                        showDropdown = false
                    }
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }

        itemsIndexed(other, key = { i, it -> it.key }) { i, searchable ->
            val icon by remember(searchable.key) {
                viewModel.getIcon(searchable, with(density) { 32.dp.roundToPx() })
            }.collectAsState(null)

            val visibility by remember(searchable.key) {
                viewModel.getVisibility(searchable)
            }.collectAsState(null)

            var showDropdown by remember { mutableStateOf(false) }

            val xs = MaterialTheme.shapes.extraSmall
            val md = MaterialTheme.shapes.medium

            Box(
                modifier = Modifier
                    .clip(
                        xs.copy(
                            topStart = if (i == 0) md.topStart else xs.topStart,
                            topEnd = if (i == 0) md.topEnd else xs.topEnd,
                            bottomStart = if (i == other.lastIndex) md.bottomStart else xs.bottomStart,
                            bottomEnd = if (i == other.lastIndex) md.bottomEnd else xs.bottomEnd
                        )
                    )
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                HiddenItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (searchable is Application || searchable is CalendarEvent) {
                                showDropdown = true
                            } else {
                                if (visibility == null) return@clickable
                                viewModel.setVisibility(
                                    searchable,
                                    if (visibility == VisibilityLevel.Default) VisibilityLevel.Hidden else VisibilityLevel.Default
                                )
                            }
                        },
                    icon = icon,
                    label = searchable.label,
                    visibility = visibility,
                )
                VisibilityDropdown(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    item = searchable,
                    value = visibility,
                    onValueChanged = {
                        viewModel.setVisibility(searchable, it)
                        showDropdown = false
                    }
                )
            }
        }
    }
}

@Composable
private fun VisibilityDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    item: SavableSearchable,
    value: VisibilityLevel?,
    onValueChanged: (VisibilityLevel) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(R.string.customize_item_visibility),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                start = 48.dp,
                top = 12.dp,
                bottom = 8.dp,
                end = 12.dp,
            )
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = null
                )
            },
            text = {
                Text(
                    when (item) {
                        is Application -> stringResource(R.string.item_visibility_app_default)
                        is CalendarEvent -> stringResource(R.string.item_visibility_calendar_default)
                        else -> stringResource(R.string.item_visibility_search_only)
                    }
                )
            },
            onClick = {
                onValueChanged(VisibilityLevel.Default)
            },
            trailingIcon = {
                if (value == VisibilityLevel.Default) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                }
            }
        )
        if (item is Application || item is CalendarEvent) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null
                    )
                },
                text = {
                    Text(stringResource(R.string.item_visibility_search_only))
                },
                onClick = {
                    onValueChanged(VisibilityLevel.SearchOnly)
                },
                trailingIcon = {
                    if (value == VisibilityLevel.SearchOnly) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = null
                        )
                    }
                }
            )
        }
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null
                )
            },
            text = {
                Text(stringResource(R.string.item_visibility_hidden))
            },
            onClick = {
                onValueChanged(VisibilityLevel.Hidden)
            },
            trailingIcon = {
                if (value == VisibilityLevel.Hidden) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                }
            }
        )
    }
}

@Composable
private fun HiddenItem(
    modifier: Modifier,
    icon: LauncherIcon?,
    label: String,
    visibility: VisibilityLevel?,
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
        if (visibility != null) {
            Icon(
                modifier = Modifier.alpha(if (visibility == VisibilityLevel.Hidden) 0.3f else 1f),
                imageVector = when (visibility) {
                    VisibilityLevel.Hidden -> Icons.Rounded.VisibilityOff
                    VisibilityLevel.Default -> Icons.Rounded.Visibility
                    VisibilityLevel.SearchOnly -> Icons.Outlined.Visibility
                },
                tint = if (visibility == VisibilityLevel.Default) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null
            )
        }
    }
}