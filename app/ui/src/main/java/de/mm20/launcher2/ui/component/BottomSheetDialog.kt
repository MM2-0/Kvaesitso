package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit,
    footerItems: @Composable (() -> Unit)? = null,
    bottomSheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    ModalBottomSheet(
        sheetState = bottomSheetState,
        onDismissRequest = onDismissRequest,
    ) {
        content(PaddingValues(horizontal = 24.dp, vertical = 8.dp))
        if (footerItems != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 16.dp,
                    alignment = Alignment.End,
                )
            ) {
                footerItems()
            }
        }
    }
}