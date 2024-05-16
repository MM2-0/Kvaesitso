package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.animateCorners
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridResults
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalGridSettings

fun LazyListScope.AppResults(
    apps: List<Application>,
    showTabs: Boolean,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    highlightedItem: Application? = null,
    columns: Int,
    reverse: Boolean,
) {

    GridResults(
        key = "app",
        items = apps,
        itemContent = {
            GridItem(
                item = it,
                showLabels = LocalGridSettings.current.showLabels,
                highlight = it.key == highlightedItem?.key
            )
        },
        before = if (showTabs) {
            {
                Column(
                    verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top,
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                MaterialTheme.shapes.medium.animateCorners(
                                    topStart = !reverse,
                                    topEnd = !reverse,
                                    bottomEnd = reverse,
                                    bottomStart = reverse,
                                )
                            ),
                        divider = {},
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { onSelectedTabChange(0) },
                            text = { Text(stringResource(R.string.apps_profile_main)) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { onSelectedTabChange(1) },
                            text = { Text(stringResource(R.string.apps_profile_work)) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        } else null,
        reverse = reverse,
        columns = columns,
    )
}