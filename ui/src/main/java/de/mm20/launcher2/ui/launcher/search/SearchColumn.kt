package de.mm20.launcher2.ui.launcher.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.PartialLauncherCard
import de.mm20.launcher2.ui.launcher.search.calculator.CalculatorItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.hidden.HiddenResults
import de.mm20.launcher2.ui.launcher.search.unitconverter.UnitConverterItem
import de.mm20.launcher2.ui.launcher.search.website.WebsiteItem
import de.mm20.launcher2.ui.launcher.search.wikipedia.WikipediaItem
import de.mm20.launcher2.ui.locals.LocalGridColumns
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

    val columns = LocalGridColumns.current

    val viewModel: SearchVM = viewModel()

    val showLabels by viewModel.showLabels.observeAsState(true)

    val hideFavs by viewModel.hideFavorites.observeAsState(true)
    val favorites by viewModel.favorites.observeAsState(emptyList())
    val apps by viewModel.appResults.observeAsState(emptyList())
    val appShortcuts by viewModel.appShortcutResults.observeAsState(emptyList())
    val contacts by viewModel.contactResults.observeAsState(emptyList())
    val files by viewModel.fileResults.observeAsState(emptyList())
    val events by viewModel.calendarResults.observeAsState(emptyList())
    val unitConverter by viewModel.unitConverterResult.observeAsState(null)
    val calculator by viewModel.calculatorResult.observeAsState(null)
    val wikipedia by viewModel.wikipediaResult.observeAsState(null)
    val website by viewModel.websiteResult.observeAsState(null)


    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = paddingValues,
        reverseLayout = reverse,
    ) {
        if (!hideFavs) {
            GridResults(
                items = favorites.toImmutableList(),
                columns = columns,
                showLabels = showLabels,
                reverse = reverse,
            )
        }
        GridResults(
            items = apps.toImmutableList(),
            columns = columns,
            showLabels = showLabels,
            reverse = reverse,
        )
        ListResults(appShortcuts.toImmutableList(), reverse)
        val uc = unitConverter
        if (uc != null) {
            SingleResult {
                UnitConverterItem(unitConverter = uc)
            }
        }
        val calc = calculator
        if (calc != null) {
            SingleResult {
                CalculatorItem(calculator = calc)
            }
        }
        ListResults(events.toImmutableList(), reverse)
        ListResults(contacts.toImmutableList(), reverse)
        val wiki = wikipedia
        if (wiki != null) {
            SingleResult {
                WikipediaItem(wikipedia = wiki)
            }
        }
        val ws = website
        if (ws != null) {
            SingleResult {
                WebsiteItem(website = ws)
            }
        }
        ListResults(files.toImmutableList(), reverse)
        item {
            HiddenResults()
        }
    }
}

fun LazyListScope.GridResults(
    items: ImmutableList<Searchable>,
    columns: Int,
    reverse: Boolean,
    showLabels: Boolean,
) {
    if (items.isEmpty()) return
    val rows = ceil(items.size / columns.toFloat()).toInt()
    items(rows) {
        GridRow(
            items = items.subList(it * columns, (it * columns + columns).coerceAtMost(items.size)),
            columns = columns,
            showLabels = showLabels,
            isFirst = if (reverse) it == rows - 1 else it == 0,
            isLast = if (reverse) it == 0 else it == rows - 1
        )
    }
}

@Composable
fun GridRow(
    modifier: Modifier = Modifier,
    items: ImmutableList<Searchable>,
    columns: Int,
    showLabels: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Box(
        modifier = modifier
            .clipToBounds()
    ) {
        PartialLauncherCard(
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                top = if (isFirst) 4.dp else 0.dp,
                bottom = if (isLast) 4.dp else 0.dp,
            ),
            isTop = isFirst,
            isBottom = isLast,
        ) {
            Row(
                modifier = Modifier.padding(
                    top = if (isFirst) 4.dp else 0.dp,
                    bottom = if (isLast) 2.dp else 0.dp,
                )
            ) {
                for (item in items) {
                    GridItem(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp, 8.dp),
                        item = item,
                        showLabels = showLabels
                    )
                }
                for (i in 0 until columns - items.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun LazyListScope.ListResults(
    items: ImmutableList<Searchable>,
    reverse: Boolean,
) {
    if (items.isEmpty()) return
    items(items.size) {
        ListRow(
            item = items[it],
            isFirst = if (reverse) it == items.size - 1 else it == 0,
            isLast = if (reverse) it == 0 else it == items.size - 1
        )
    }
}

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    item: Searchable,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Box(
        modifier = modifier
            .clipToBounds()
    ) {
        PartialLauncherCard(
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                top = if (isFirst) 4.dp else 0.dp,
                bottom = if (isLast) 4.dp else 0.dp,
            ),
            isTop = isFirst,
            isBottom = isLast,
        ) {
            Box(
                modifier = Modifier.padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = if (isFirst) 8.dp else 4.dp,
                    bottom = if (isLast) 8.dp else 4.dp,
                )
            ) {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth(),
                    item = item
                )
            }
        }
    }
}

fun LazyListScope.SingleResult(content: @Composable (() -> Unit)?) {
    if (content == null) return
    item {
        LauncherCard(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            )
        ) {
            content()
        }
    }
}