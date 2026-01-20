package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid

@Composable
fun HiddenItemsSheet(
    expanded: Boolean,
    items: List<SavableSearchable>,
    onDismiss: () -> Unit
) {
    DismissableBottomSheet(expanded = expanded, onDismissRequest = onDismiss) {
        SearchResultGrid(
            items,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding()
        )
    }
}
