package de.mm20.launcher2.ui.launcher.search.hidden

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.LauncherActivityVM
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
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shadowElevation = 2.dp,
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.large,
                onClick = { showHiddenItems = true }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                    )
                }

            }
        }
    }

    if (showHiddenItems) {
        HiddenItemsSheet(hiddenResults, onDismiss = {showHiddenItems = false})
    }
}