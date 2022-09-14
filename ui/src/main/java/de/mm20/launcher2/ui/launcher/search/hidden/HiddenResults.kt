package de.mm20.launcher2.ui.launcher.search.hidden

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.modals.HiddenItemsSheet
import de.mm20.launcher2.ui.launcher.search.SearchVM

@Composable
fun HiddenResults() {
    val viewModel: SearchVM = viewModel()
    val hiddenResults by viewModel.hiddenResults.observeAsState(
        emptyList()
    )

    var showHiddenItems by remember { mutableStateOf(false) }

    if (hiddenResults.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            FloatingActionButton(
                elevation = FloatingActionButtonDefaults.loweredElevation(),
                onClick = { showHiddenItems = true }) {
                Icon(imageVector = Icons.Rounded.VisibilityOff, contentDescription = null)
            }
        }
    }

    if (showHiddenItems) {
        HiddenItemsSheet(hiddenResults, onDismiss = {showHiddenItems = false})
    }
}