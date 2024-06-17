package de.mm20.launcher2.ui.launcher.sheets

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.OutlinedTagsInputField
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun CustomizeSearchableSheet(
    searchable: SavableSearchable,
    onDismiss: () -> Unit,
) {
    val viewModel: CustomizeSearchableSheetVM =
        remember(searchable.key) { CustomizeSearchableSheetVM(searchable) }
    val context = LocalContext.current

    val pickIcon by viewModel.isIconPickerOpen

    if (pickIcon) {
        BackHandler {
            viewModel.closeIconPicker()
        }
    }

    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = if (pickIcon) {
            {
                Text(stringResource(R.string.icon_picker_title))
            }
        } else null,
        dismissible = { !pickIcon },
        confirmButton = if (pickIcon) {
            {
                OutlinedButton(onClick = { viewModel.closeIconPicker() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        } else null,
        zIndex = 100f,
    ) {
        if (!pickIcon) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(it),
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
                            icon = BadgeIcon(Icons.Rounded.Edit)
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
                        Icon(Icons.AutoMirrored.Rounded.Label, null)
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
                        Icon(Icons.Rounded.Tag, null)
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
                                when (visibility) {
                                    VisibilityLevel.Default -> Icons.Rounded.Visibility
                                    VisibilityLevel.SearchOnly -> Icons.Outlined.Visibility
                                    VisibilityLevel.Hidden -> Icons.Rounded.VisibilityOff
                                },
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
                                    Icon(Icons.Rounded.Visibility, null)
                                }
                            )
                        } else if (searchable is CalendarEvent) {
                            DropdownMenuItem(
                                onClick = {
                                    visibility = VisibilityLevel.Default
                                    showDropdown = false
                                },
                                text = {
                                    Text(stringResource(R.string.item_visibility_app_default))
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Visibility, null)
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
                                    Icon(Icons.Rounded.Visibility, null)
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
                                        Icons.Outlined.Visibility,
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
                                Icon(Icons.Rounded.VisibilityOff, null)
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
        } else {
            val iconSize = 48.dp
            val iconSizePx = iconSize.toPixels()

            val scope = rememberCoroutineScope()

            val suggestions by remember { viewModel.getIconSuggestions(iconSizePx.toInt()) }
                .collectAsState(emptyList())

            val defaultIcon by remember {
                viewModel.getDefaultIcon(iconSizePx.toInt())
            }.collectAsState(null)

            var query by remember { mutableStateOf("") }
            var filterIconPack by remember { mutableStateOf<IconPack?>(null) }
            val isSearching by viewModel.isSearchingIcons
            val iconResults by viewModel.iconSearchResults

            var showIconPackFilter by remember { mutableStateOf(false) }
            val installedIconPacks by viewModel.installedIconPacks.collectAsState(null)
            val noPacksInstalled = installedIconPacks?.isEmpty() == true

            val columns = LocalGridSettings.current.columnCount

            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(columns),
                contentPadding = it,
            ) {

                item(span = { GridItemSpan(columns) }) {
                    OutlinedTextField(
                        modifier = Modifier.padding(bottom = 16.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null
                            )
                        },
                        enabled = !noPacksInstalled,
                        placeholder = {
                            Text(
                                stringResource(
                                    if (noPacksInstalled) R.string.icon_picker_no_packs_installed else R.string.icon_picker_search_icon
                                )
                            )
                        },
                        value = query,
                        onValueChange = {
                            query = it
                            scope.launch {
                                viewModel.searchIcon(query, filterIconPack)
                            }
                        },
                        singleLine = true,
                    )
                }

                if (query.isEmpty()) {
                    if (defaultIcon != null) {
                        item(span = { GridItemSpan(columns) }) {
                            Separator(stringResource(R.string.icon_picker_default_icon))
                        }
                        item {
                            IconPreview(item = defaultIcon, iconSize = iconSize, onClick = {
                                viewModel.pickIcon(null)
                            })
                        }
                    }
                    item(span = { GridItemSpan(columns) }) {
                        Separator(stringResource(R.string.icon_picker_suggestions))
                    }

                    items(suggestions) {
                        IconPreview(
                            it,
                            iconSize,
                            onClick = { viewModel.pickIcon(it.customIcon) }
                        )
                    }
                } else {

                    if (!installedIconPacks.isNullOrEmpty()) {
                        item(
                            span = { GridItemSpan(columns) },
                        ) {
                            Button(
                                onClick = { showIconPackFilter = !showIconPackFilter },
                                modifier = Modifier
                                    .wrapContentWidth(align = Alignment.CenterHorizontally)
                                    .padding(bottom = 16.dp),
                                contentPadding = PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                            ) {
                                if (filterIconPack == null) {
                                    Icon(
                                        modifier = Modifier
                                            .padding(end = ButtonDefaults.IconSpacing)
                                            .size(ButtonDefaults.IconSize),
                                        imageVector = Icons.Rounded.FilterAlt,
                                        contentDescription = null
                                    )
                                } else {
                                    val icon = remember(filterIconPack?.packageName) {
                                        try {
                                            filterIconPack?.packageName?.let { pkg ->
                                                context.packageManager.getApplicationIcon(pkg)
                                            }
                                        } catch (e: PackageManager.NameNotFoundException) {
                                            null
                                        }
                                    }
                                    AsyncImage(
                                        modifier = Modifier
                                            .padding(end = ButtonDefaults.IconSpacing)
                                            .size(ButtonDefaults.IconSize),
                                        model = icon,
                                        contentDescription = null
                                    )
                                }
                                DropdownMenu(
                                    expanded = showIconPackFilter,
                                    onDismissRequest = { showIconPackFilter = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.icon_picker_filter_all_packs)) },
                                        onClick = {
                                            showIconPackFilter = false
                                            filterIconPack = null
                                            scope.launch {
                                                viewModel.searchIcon(query, filterIconPack)
                                            }
                                        }
                                    )
                                    installedIconPacks?.forEach { iconPack ->
                                        DropdownMenuItem(
                                            onClick = {
                                                showIconPackFilter = false
                                                filterIconPack = iconPack
                                                scope.launch {
                                                    viewModel.searchIcon(query, filterIconPack)
                                                }
                                            },
                                            text = {
                                                Text(iconPack.name)
                                            })
                                    }
                                }
                                Text(
                                    text = filterIconPack?.name
                                        ?: stringResource(id = R.string.icon_picker_filter_all_packs),
                                    modifier = Modifier.animateContentSize()
                                )
                                Icon(
                                    Icons.Rounded.ArrowDropDown,
                                    modifier = Modifier
                                        .padding(start = ButtonDefaults.IconSpacing)
                                        .size(ButtonDefaults.IconSize),
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    items(iconResults) {
                        IconPreview(
                            it,
                            iconSize,
                            onClick = { viewModel.pickIcon(it.customIcon) }
                        )
                    }

                    if (isSearching) {
                        item(span = { GridItemSpan(columns) }) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(24.dp)
                                )
                            }
                        }
                    }
                }

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