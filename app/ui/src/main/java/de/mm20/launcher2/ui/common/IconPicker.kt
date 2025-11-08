package de.mm20.launcher2.ui.common

import android.content.pm.PackageManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.sheets.IconPreview
import de.mm20.launcher2.ui.launcher.sheets.Separator
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlinx.coroutines.launch

@Composable
fun IconPicker(
    searchable: SavableSearchable,
    onSelect: (CustomIcon?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val iconSize = 48.dp
    val iconSizePx = iconSize.toPixels()

    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    val viewModel: IconPickerVM =
        remember(searchable.key) { IconPickerVM(searchable) }

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
    val packsInstalled = installedIconPacks?.isEmpty() == false

    val columns = LocalGridSettings.current.columnCount

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(columns),
        contentPadding = contentPadding,
    ) {
        if (packsInstalled) {
            item(span = { GridItemSpan(columns) }) {
                SearchBar(
                    windowInsets = WindowInsets(0.dp),
                    expanded = false,
                    onExpandedChange = {},
                    inputField = {
                        SearchBarDefaults.InputField(
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.search_24px),
                                    contentDescription = null
                                )
                            },
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = {
                                Text(stringResource(R.string.icon_picker_search_icon))
                            },
                            query = query,
                            onQueryChange = {
                                query = it
                                scope.launch {
                                    viewModel.searchIcon(query, filterIconPack)
                                }
                            },
                        )
                    }
                ) {

                }
            }
        }

        if (query.isEmpty()) {
            if (defaultIcon != null) {
                item(span = { GridItemSpan(columns) }) {
                    Separator(stringResource(R.string.icon_picker_default_icon))
                }
                item {
                    IconPreview(item = defaultIcon, iconSize = iconSize, onClick = {
                        onSelect(null)
                    })
                }
            }

            if (suggestions.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Separator(stringResource(R.string.icon_picker_suggestions))
                }
                items(suggestions) {
                    IconPreview(
                        it,
                        iconSize,
                        onClick = { onSelect(it.customIcon) }
                    )
                }
            }
        } else {
            item(span = { GridItemSpan(columns) }) {
                Button(
                    onClick = { showIconPackFilter = !showIconPackFilter },
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.CenterHorizontally)
                        .padding(16.dp),
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
                            painter = painterResource(R.drawable.filter_alt_20px),
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
                        painterResource(R.drawable.arrow_drop_down_20px),
                        modifier = Modifier
                            .padding(start = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize),
                        contentDescription = null
                    )
                }
            }

            items(iconResults) {
                IconPreview(
                    it,
                    iconSize,
                    onClick = { onSelect(it.customIcon) }
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