package de.mm20.launcher2.ui.launcher.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.PartialLauncherCard
import de.mm20.launcher2.ui.launcher.search.calculator.CalculatorItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.favorites.SearchFavoritesVM
import de.mm20.launcher2.ui.launcher.search.unitconverter.UnitConverterItem
import de.mm20.launcher2.ui.launcher.search.website.WebsiteItem
import de.mm20.launcher2.ui.launcher.search.wikipedia.WikipediaItem
import de.mm20.launcher2.ui.launcher.sheets.HiddenItemsSheet
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.ceil

@Composable
fun SearchColumn(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    reverse: Boolean = false,
) {

    val columns = LocalGridSettings.current.columnCount
    val context = LocalContext.current

    val viewModel: SearchVM = viewModel()

    val favoritesVM: SearchFavoritesVM = viewModel()
    val favorites by favoritesVM.favorites.collectAsState(emptyList())

    var showWorkProfileApps by remember { mutableStateOf(false) }

    val hideFavs by viewModel.hideFavorites
    val favoritesEnabled by viewModel.favoritesEnabled.collectAsState(false)
    val apps by viewModel.appResults
    val workApps by viewModel.workAppResults
    val appShortcuts by viewModel.appShortcutResults
    val contacts by viewModel.contactResults
    val files by viewModel.fileResults
    val events by viewModel.calendarResults
    val unitConverter by viewModel.unitConverterResults
    val calculator by viewModel.calculatorResults
    val wikipedia by viewModel.wikipediaResults
    val website by viewModel.websiteResults
    val hiddenResults by viewModel.hiddenResults

    val bestMatch by viewModel.bestMatch

    val isSearchEmpty by viewModel.isSearchEmpty

    val missingCalendarPermission by viewModel.missingCalendarPermission.collectAsState(false)
    val missingShortcutsPermission by viewModel.missingAppShortcutPermission.collectAsState(false)
    val missingContactsPermission by viewModel.missingContactsPermission.collectAsState(false)
    val missingFilesPermission by viewModel.missingFilesPermission.collectAsState(false)

    val pinnedTags by favoritesVM.pinnedTags.collectAsState(emptyList())
    val selectedTag by favoritesVM.selectedTag.collectAsState(null)
    val tagsScrollState = rememberScrollState()
    val favoritesEditButton by favoritesVM.showEditButton.collectAsState(false)

    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = paddingValues,
        reverseLayout = reverse,
    ) {
        if (!hideFavs && favoritesEnabled) {
            GridResults(
                items = favorites.toImmutableList(),
                columns = columns,
                key = "favorites",
                reverse = reverse,
                before = if (favorites.isEmpty()) {
                    {
                        Banner(
                            modifier = Modifier.padding(16.dp),
                            text = stringResource(
                                if (selectedTag == null) R.string.favorites_empty else R.string.favorites_empty_tag
                            ),
                            icon = if (selectedTag == null) Icons.Rounded.Star else Icons.Rounded.Tag,
                        )
                    }
                } else null,
                after = if (pinnedTags.isEmpty() && !favoritesEditButton) {
                    null
                } else {
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (reverse) 8.dp else 4.dp,
                                    bottom = if (reverse) 4.dp else 8.dp,
                                    end = if (favoritesEditButton) 8.dp else 0.dp
                                ),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(tagsScrollState)
                                    .padding(end = 12.dp),
                            ) {
                                FilterChip(
                                    modifier = Modifier.padding(start = 16.dp),
                                    selected = selectedTag == null,
                                    onClick = { favoritesVM.selectTag(null) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text(stringResource(R.string.favorites)) }
                                )
                                for (tag in pinnedTags) {
                                    FilterChip(
                                        modifier = Modifier.padding(start = 8.dp),
                                        selected = selectedTag == tag.tag,
                                        onClick = { favoritesVM.selectTag(tag.tag) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Tag,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text(tag.label) }
                                    )
                                }
                            }
                            if (favoritesEditButton) {
                                val sheetManager = LocalBottomSheetManager.current
                                SmallFloatingActionButton(
                                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                                    onClick = { sheetManager.showEditFavoritesSheet() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                },
                highlightedItem = bestMatch as? SavableSearchable
            )
        }
        GridResults(
            items = if ((showWorkProfileApps || apps.isEmpty()) && workApps.isNotEmpty()) workApps.toImmutableList() else apps.toImmutableList(),
            columns = columns,
            reverse = reverse,
            key = "apps",
            before = if (workApps.isNotEmpty() && apps.isNotEmpty()) {
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(
                                top = if (reverse) 4.dp else 8.dp,
                                bottom = if (reverse) 8.dp else 4.dp
                            ),
                    ) {
                        FilterChip(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            selected = !showWorkProfileApps,
                            onClick = { showWorkProfileApps = false },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Person, contentDescription = null)
                            },
                            label = {
                                Text(
                                    stringResource(R.string.apps_profile_main),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        FilterChip(
                            selected = showWorkProfileApps,
                            onClick = { showWorkProfileApps = true },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Work, contentDescription = null)
                            },
                            label = {
                                Text(
                                    stringResource(R.string.apps_profile_work),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            } else null,
            highlightedItem = bestMatch as? SavableSearchable
        )
        ListResults(
            before = if (missingShortcutsPermission && !isSearchEmpty) {
                {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(
                            R.string.missing_permission_appshortcuts_search,
                            stringResource(R.string.app_name)
                        ),
                        onClick = { viewModel.requestAppShortcutPermission(context as AppCompatActivity) },
                        secondaryAction = {
                            OutlinedButton(onClick = {
                                viewModel.disableAppShortcutSearch()
                            }) {
                                Text(
                                    stringResource(R.string.turn_off),
                                )
                            }
                        }
                    )
                }
            } else null,
            items = appShortcuts.toImmutableList(),
            reverse = reverse,
            key = "shortcuts",
            highlightedItem = bestMatch as? SavableSearchable
        )
        for (conv in unitConverter) {
            SingleResult {
                UnitConverterItem(unitConverter = conv)
            }
        }
        for (calc in calculator) {
            SingleResult {
                CalculatorItem(calculator = calc)
            }
        }
        ListResults(
            before = if (missingCalendarPermission && !isSearchEmpty) {
                {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.missing_permission_calendar_search),
                        onClick = { viewModel.requestCalendarPermission(context as AppCompatActivity) },
                        secondaryAction = {
                            OutlinedButton(onClick = {
                                viewModel.disableCalendarSearch()
                            }) {
                                Text(
                                    stringResource(R.string.turn_off),
                                )
                            }
                        }
                    )
                }
            } else null,
            items = events.toImmutableList(),
            reverse = reverse,
            key = "events",
            highlightedItem = bestMatch as? SavableSearchable
        )
        ListResults(
            before = if (missingContactsPermission && !isSearchEmpty) {
                {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.missing_permission_contact_search),
                        onClick = { viewModel.requestContactsPermission(context as AppCompatActivity) },
                        secondaryAction = {
                            OutlinedButton(onClick = {
                                viewModel.disableContactsSearch()
                            }) {
                                Text(
                                    stringResource(R.string.turn_off),
                                )
                            }
                        }
                    )
                }
            } else null,
            items = contacts.toImmutableList(),
            reverse = reverse,
            key = "contacts",
            highlightedItem = bestMatch as? SavableSearchable
        )
        for (wiki in wikipedia) {
            SingleResult {
                WikipediaItem(wikipedia = wiki)
            }
        }
        for (ws in website) {
            SingleResult {
                WebsiteItem(website = ws)
            }
        }
        ListResults(
            before = if (missingFilesPermission && !isSearchEmpty) {
                {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.missing_permission_files_search),
                        onClick = { viewModel.requestFilesPermission(context as AppCompatActivity) },
                        secondaryAction = {
                            OutlinedButton(onClick = {
                                viewModel.disableFilesSearch()
                            }) {
                                Text(
                                    stringResource(R.string.turn_off),
                                )
                            }
                        }
                    )
                }
            } else null,
            items = files.toImmutableList(),
            reverse = reverse,
            key = "files",
            highlightedItem = bestMatch as? SavableSearchable
        )
    }

    val sheetManager = LocalBottomSheetManager.current
    if (sheetManager.hiddenItemsSheetShown.value) {
        HiddenItemsSheet(items = hiddenResults, onDismiss = { sheetManager.dismissHiddenItemsSheet() })
    }
}

fun LazyListScope.GridResults(
    items: ImmutableList<SavableSearchable>,
    columns: Int,
    reverse: Boolean,
    key: String,
    before: (@Composable () -> Unit)? = null,
    after: (@Composable () -> Unit)? = null,
    highlightedItem: SavableSearchable?
) {
    if (items.isEmpty() && before == null && after == null) return

    if (before != null) {
        item(key = "$key-before") {
            PartialCardRow(
                isFirst = true,
                isLast = items.isEmpty() && after == null,
                reverse = reverse
            ) {
                before()
            }
        }
    }

    val rows = ceil(items.size / columns.toFloat()).toInt()
    items(
        rows,
        key = {
            "$key-${items[it * columns].key}"
        }
    ) {
        PartialCardRow(
            isFirst = it == 0 && before == null,
            isLast = it == rows - 1 && after == null,
            reverse = reverse
        ) {
            GridRow(
                modifier = Modifier.padding(
                    top = if (if (reverse) it == rows - 1 else it == 0) 4.dp else 0.dp,
                    bottom = if (if (reverse) it == 0 else it == rows - 1) 2.dp else 0.dp,
                ),
                items = items.subList(
                    it * columns,
                    (it * columns + columns).coerceAtMost(items.size)
                ),
                columns = columns,
                highlightedItem = highlightedItem
            )
        }
    }

    if (after != null) {
        item(key = "$key-after") {
            PartialCardRow(
                isFirst = items.isEmpty() && before == null,
                isLast = true,
                reverse = reverse
            ) {
                after()
            }
        }
    }
}

@Composable
fun GridRow(
    modifier: Modifier = Modifier,
    items: ImmutableList<SavableSearchable>,
    columns: Int,
    showLabels: Boolean = LocalGridSettings.current.showLabels,
    highlightedItem: SavableSearchable?
) {

    Row(
        modifier = modifier
    ) {
        for (item in items) {
            GridItem(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp),
                item = item,
                showLabels = showLabels,
                highlight = item.key == highlightedItem?.key
            )
        }
        for (i in 0 until columns - items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

fun LazyListScope.ListResults(
    items: ImmutableList<SavableSearchable>,
    reverse: Boolean,
    key: String,
    before: (@Composable () -> Unit)? = null,
    after: (@Composable () -> Unit)? = null,
    highlightedItem: SavableSearchable?
) {
    if (before != null) {
        item(key = "$key-before") {
            PartialCardRow(
                isFirst = true,
                isLast = items.isEmpty() && after == null,
                reverse = reverse
            ) {
                before()
            }
        }
    }
    items(
        items.size,
        key = {
            "$key-${items[it].key}"
        }
    ) {
        PartialCardRow(
            isFirst = it == 0 && before == null,
            isLast = it == items.lastIndex && after == null,
            reverse = reverse
        ) {
            ListRow(
                modifier = Modifier.padding(
                    top = if (if (reverse) it == items.size - 1 else it == 0) 8.dp else 4.dp,
                    bottom = if (if (reverse) it == 0 else it == items.size - 1) 8.dp else 4.dp,
                ),
                item = items[it],
                highlight = items[it].key == highlightedItem?.key
            )
        }
    }
    if (after != null) {
        item(key = "$key-after") {
            PartialCardRow(
                isFirst = items.isEmpty() && before == null,
                isLast = true,
                reverse = reverse
            ) {
                after()
            }
        }
    }
}

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    item: SavableSearchable,
    highlight: Boolean
) {
    Box(
        modifier = modifier.padding(
            start = 8.dp,
            end = 8.dp,
        )
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth(),
            item = item,
            highlight = highlight
        )
    }
}

fun LazyListScope.SingleResult(content: @Composable (() -> Unit)?) {
    if (content == null) return
    item {
        LauncherCard(
            modifier = Modifier
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp,
                )
                .animateItemPlacement()
        ) {
            content()
        }
    }
}

@Composable
fun LazyItemScope.PartialCardRow(
    modifier: Modifier = Modifier,
    isFirst: Boolean,
    isLast: Boolean,
    reverse: Boolean,
    content: @Composable () -> Unit
) {
    val isTop = isFirst && !reverse || isLast && reverse
    val isBottom = isLast && !reverse || isFirst && reverse
    Box(
        modifier = modifier
            .clipToBounds()
            .animateItemPlacement()
    ) {
        PartialLauncherCard(
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                top = if (isTop) 4.dp else 0.dp,
                bottom = if (isBottom) 4.dp else 0.dp,
            ),
            isTop = isTop,
            isBottom = isBottom,
        ) {
            content()
        }
    }
}