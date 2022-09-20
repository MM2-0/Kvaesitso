package de.mm20.launcher2.ui.launcher.widgets.favorites

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.modals.EditFavoritesSheet
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid

@Composable
fun FavoritesWidget() {
    val viewModel: FavoritesWidgetVM = viewModel()
    val favorites by remember { viewModel.favorites }.collectAsState(emptyList())
    var showEditFavoritesDialog by remember { mutableStateOf(false) }

    Column {
        SearchResultGrid(favorites)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 4.dp,
                    bottom = 8.dp,
                    end = 8.dp
                ),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    modifier = Modifier.padding(start = 16.dp),
                    selected = true,
                    onClick = { /*TODO*/ },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.favorites)) }
                )
            }
            SmallFloatingActionButton(
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                onClick = { showEditFavoritesDialog = true }
            ) {
                Icon(imageVector = Icons.Rounded.Edit, contentDescription = null)
            }
        }
    }

    if (showEditFavoritesDialog) {
        EditFavoritesSheet(
            onDismiss = { showEditFavoritesDialog = false }
        )
    }
}