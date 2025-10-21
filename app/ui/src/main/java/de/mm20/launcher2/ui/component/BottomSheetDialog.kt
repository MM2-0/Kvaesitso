package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit,
    bottomSheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 8.dp),
        sheetState = bottomSheetState,
        onDismissRequest = onDismissRequest,
    ) {
        content(PaddingValues(horizontal = 24.dp, vertical = 8.dp))
    }
}