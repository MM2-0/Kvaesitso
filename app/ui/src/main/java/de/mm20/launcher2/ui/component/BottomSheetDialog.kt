package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.ktx.toDp

@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit,
    bottomSheetState: SheetState = rememberModalBottomSheetState(),
    windowInsets: WindowInsets = WindowInsets(left = 24.dp, right = 24.dp),
    content: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 8.dp),
        sheetState = bottomSheetState,
        onDismissRequest = onDismissRequest,
        contentWindowInsets = { windowInsets }
    ) {
        content(PaddingValues(
            bottom = WindowInsets.navigationBars.getBottom(LocalDensity.current).toDp(),
        ))
    }
}