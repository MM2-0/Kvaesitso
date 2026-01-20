package de.mm20.launcher2.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels

@Composable
fun SearchablePicker(
    expanded: Boolean,
    value: SavableSearchable?,
    onValueChanged: (SavableSearchable?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    DismissableBottomSheet(expanded = expanded, onDismissRequest = onDismissRequest) {
        val viewModel: SearchablePickerVM = viewModel()

        val colorSurface = MaterialTheme.colorScheme.surfaceContainerLow

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding(),
                top = 16.dp,
            )
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
            items(viewModel.items) {
                val iconSize = 32.dp.toPixels()
                val icon by remember(it.key) {
                    viewModel.getIcon(
                        it,
                        iconSize.toInt()
                    )
                }.collectAsStateWithLifecycle(null)
                val selected = it.key == value?.key
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                    ),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    onClick = { onValueChanged(it) }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShapedLauncherIcon(
                            icon = { icon },
                            size = 32.dp,
                        )
                        Text(
                            text = it.label,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            Icon(
                                painterResource(R.drawable.check_circle_24px_filled),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}