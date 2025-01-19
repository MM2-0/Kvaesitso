package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid

@Composable
fun HiddenItemsSheet(
    items: List<SavableSearchable>,
    onDismiss: () -> Unit
) {
    val viewModel: HiddenItemsSheetVM = viewModel()

    val context = LocalContext.current

    BottomSheetDialog(
        onDismissRequest = onDismiss,
        actions = {
            IconButton(onClick = { viewModel.showHiddenItems(context) }) {
                Icon(imageVector = Icons.Rounded.Edit, contentDescription = null)
            }
        },
    ) {

        SearchResultGrid(
            items,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(it)
        )
    }
}
