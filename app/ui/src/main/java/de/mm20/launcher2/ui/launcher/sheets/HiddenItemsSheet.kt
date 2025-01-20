package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid

@Composable
fun HiddenItemsSheet(
    items: List<SavableSearchable>,
    onDismiss: () -> Unit
) {
    BottomSheetDialog(onDismiss) {
        SearchResultGrid(
            items,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(it)
        )
    }
}
