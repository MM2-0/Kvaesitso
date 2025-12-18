package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.IconPicker
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.OutlinedTagsInputField
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun CustomizeSearchableSheet(
    searchable: SavableSearchable,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel: CustomizeSearchableSheetVM =
        remember(searchable.key) { CustomizeSearchableSheetVM(searchable) }

    val pickIcon by viewModel.isIconPickerOpen

    BottomSheetDialog(onDismissRequest = { if (!pickIcon) onDismiss() }) {
        Column(
            modifier = Modifier.padding(it),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val iconSize = 64.dp
            val iconSizePx = iconSize.toPixels()
            val icon by remember { viewModel.getIcon(iconSizePx.toInt()) }.collectAsState(null)

            ShapedLauncherIcon(
                size = iconSize,
                icon = { icon },
                badge = {
                    Badge(
                        icon = BadgeIcon(R.drawable.edit_20px)
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.openIconPicker()
                }
            )

            var customLabelValue by remember {
                mutableStateOf(searchable.labelOverride ?: "")
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                value = customLabelValue,
                onValueChange = {
                    customLabelValue = it
                },
                singleLine = true,
                label = {
                    Text(stringResource(R.string.customize_item_label))
                },
                placeholder = {
                    Text(searchable.label)
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.label_24px), null)
                }
            )

            var tags by remember { mutableStateOf(emptyList<String>()) }
            var visibility by remember { mutableStateOf(VisibilityLevel.Default) }

            LaunchedEffect(searchable.key) {
                visibility = viewModel.getVisibility().first()
                tags = viewModel.getTags().first()
            }

            OutlinedTagsInputField(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                tags = tags, onTagsChange = { tags = it.distinct() },
                label = {
                    Text(stringResource(R.string.customize_item_tags))
                },
                onAutocomplete = {
                    viewModel.autocompleteTags(it).minus(tags.toSet())
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.tag_24px), null)
                }
            )

            var showDropdown by remember {
                mutableStateOf(false)
            }

            ExposedDropdownMenuBox(
                expanded = showDropdown,
                onExpandedChange = { showDropdown = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    value = when (visibility) {
                        VisibilityLevel.Default -> {
                            when (searchable) {
                                is Application -> stringResource(R.string.item_visibility_app_default)
                                is CalendarEvent -> stringResource(R.string.item_visibility_calendar_default)
                                else -> stringResource(R.string.item_visibility_search_only)
                            }
                        }

                        VisibilityLevel.SearchOnly -> stringResource(R.string.item_visibility_search_only)
                        VisibilityLevel.Hidden -> stringResource(R.string.item_visibility_hidden)
                    },
                    label = {
                        Text(stringResource(R.string.customize_item_visibility))
                    },
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                    leadingIcon = {
                        Icon(
                            painterResource(
                                when (visibility) {
                                    VisibilityLevel.Default -> R.drawable.visibility_24px_filled
                                    VisibilityLevel.SearchOnly -> R.drawable.visibility_24px
                                    VisibilityLevel.Hidden -> R.drawable.visibility_off_24px
                                }
                            ),
                            null
                        )
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = {
                        showDropdown = false
                    }
                ) {
                    if (searchable is Application) {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.Default
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_app_default))
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.visibility_24px_filled), null)
                            }
                        )
                    } else if (searchable is CalendarEvent) {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.Default
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_calendar_default))
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.visibility_24px_filled), null)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.Default
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_search_only))
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.visibility_24px_filled), null)
                            }
                        )
                    }
                    if (searchable is Application || searchable is CalendarEvent) {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.SearchOnly
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_search_only))
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.visibility_24px),
                                    null
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        onClick = {
                            visibility = VisibilityLevel.Hidden
                            showDropdown = false
                        },
                        text = {
                            Text(stringResource(R.string.item_visibility_hidden))
                        },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.visibility_off_24px), null)
                        }
                    )
                }
            }

            DisposableEffect(searchable.key) {
                onDispose {
                    viewModel.setCustomLabel(customLabelValue)
                    viewModel.setTags(tags)
                    viewModel.setVisibility(visibility)
                }
            }
        }
        if (pickIcon) {
            val bottomSheetState = rememberModalBottomSheetState()
            BottomSheetDialog(
                onDismissRequest = { viewModel.closeIconPicker() },
                bottomSheetState = bottomSheetState
            ) {
                IconPicker(
                    searchable = searchable,
                    onSelect = {
                        scope.launch {
                            viewModel.pickIcon(it)
                            bottomSheetState.hide()
                            viewModel.closeIconPicker()
                        }
                    },
                    contentPadding = it,
                )
            }
        }
    }
}

@Composable
fun IconPreview(
    item: CustomIconWithPreview?,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        ShapedLauncherIcon(
            size = iconSize,
            icon = { item?.preview },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
fun Separator(label: String) {
    Text(
        label,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
    )
}