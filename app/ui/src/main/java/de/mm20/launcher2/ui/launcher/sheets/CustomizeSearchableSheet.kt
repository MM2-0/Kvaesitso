package de.mm20.launcher2.ui.launcher.sheets

import android.content.pm.PackageManager
import android.graphics.drawable.InsetDrawable
import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.search.SavableSearchable
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
        title = {
            Text(stringResource(if (pickIcon) R.string.icon_picker_title else R.string.menu_customize))
        },
        dismissible = { !pickIcon },
        confirmButton = {
            if (pickIcon) {
                OutlinedButton(onClick = { viewModel.closeIconPicker() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            } else {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.close))
                }
            }
        }
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
                val primaryColor = MaterialTheme.colorScheme.onSecondary
                val badgeDrawable = remember {
                    InsetDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_edit),
                        8
                    ).also {
                        it.setTint(primaryColor.toArgb())
                    }
                }

                ShapedLauncherIcon(
                    size = iconSize,
                    icon = { icon },
                    badge = {
                        Badge(
                            icon = badgeDrawable
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
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    value = customLabelValue,
                    onValueChange = {
                        customLabelValue = it
                    },
                    singleLine = true,
                    placeholder = {
                        Text(searchable.label)
                    },
                )

                var tags by remember { mutableStateOf(emptyList<String>()) }

                LaunchedEffect(searchable.key) {
                    tags = viewModel.getTags().first()
                }

                OutlinedTagsInputField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    tags = tags, onTagsChange = { tags = it.distinct() },
                    placeholder = {
                        Text(stringResource(R.string.customize_tags_placeholder))
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    onAutocomplete = {
                        viewModel.autocompleteTags(it).minus(tags.toSet())
                    }
                )

                DisposableEffect(searchable.key) {
                    onDispose {
                        viewModel.setCustomLabel(customLabelValue)
                        viewModel.setTags(tags)
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
                                    text = filterIconPack?.name ?: stringResource(id = R.string.icon_picker_filter_all_packs),
                                    modifier = Modifier.animateContentSize()
                                )
                                Icon(Icons.Rounded.ArrowDropDown,
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