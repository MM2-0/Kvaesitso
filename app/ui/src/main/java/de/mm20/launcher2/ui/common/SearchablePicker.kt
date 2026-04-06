package de.mm20.launcher2.ui.common

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.animateShapeAsState
import de.mm20.launcher2.ui.ktx.toPixels

@Composable
fun SearchablePicker(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    value: SavableSearchable?,
    onValueChanged: (SavableSearchable?) -> Unit,
) {
    val viewModel: SearchablePickerVM = viewModel()

    val colorSurface = MaterialTheme.colorScheme.surfaceContainerLow

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        stickyHeader {
            DockedSearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.5f to colorSurface,
                                0.5f to colorSurface.copy(alpha = 0f),
                            )
                        )
                    }
                    .padding(bottom = 16.dp),
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
                        trailingIcon = {
                            if (viewModel.searchQuery.isNotEmpty()) {
                                IconButton(
                                    modifier = Modifier.offset(16.dp),
                                    onClick = {
                                        viewModel.onSearchQueryChanged("")
                                    }) {
                                    Icon(painterResource(R.drawable.close_24px), null)
                                }
                            }
                        },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        query = viewModel.searchQuery,
                        onQueryChange = {
                            viewModel.onSearchQueryChanged(it)
                        },
                        placeholder = {
                            Text(stringResource(R.string.search_bar_placeholder))
                        },
                    )
                }
            ) {
            }
        }
        itemsIndexed(viewModel.items) { i, it ->
            val iconSize = 32.dp.toPixels()
            val icon by remember(it.key) {
                viewModel.getIcon(
                    it,
                    iconSize.toInt()
                )
            }.collectAsStateWithLifecycle(null)

            val badge by remember(it.key) {
                viewModel.getBadge(it)
            }.collectAsStateWithLifecycle(null)


            val selected = it.key == value?.key

            val transition = updateTransition(selected)
            val background by transition.animateColor {
                if (it) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceBright
            }
            val foreground by transition.animateColor {
                if (it) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            }

            val md = MaterialTheme.shapes.medium
            val xs = MaterialTheme.shapes.extraSmall

            val shape by animateShapeAsState(
                if (selected || viewModel.items.size == 1) md
                else when (i) {
                    0 -> md.copy(bottomStart = xs.bottomStart, bottomEnd = xs.bottomEnd)
                    viewModel.items.lastIndex -> md.copy(topStart = xs.topStart, topEnd = xs.topEnd)
                    else -> xs
                }
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = shape,
                color = background,
                contentColor = foreground,
                onClick = { onValueChanged(it) }) {
                Row(
                    modifier = Modifier
                        .padding(
                            start = 8.dp,
                            end = if (selected) 8.dp else 16.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShapedLauncherIcon(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        icon = { icon },
                        badge = { badge },
                        size = 36.dp,
                    )
                    Text(
                        text = it.label,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp, top = 16.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) {
                        Icon(
                            painterResource(R.drawable.check_24px),
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                            tint = foreground,
                        )
                    }
                }
            }
        }
    }
}
