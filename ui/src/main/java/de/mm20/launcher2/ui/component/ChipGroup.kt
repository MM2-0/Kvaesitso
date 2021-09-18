package de.mm20.launcher2.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow

@Composable
fun ChipGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier,
        content = content,
        mainAxisSpacing = 16.dp,
        crossAxisSpacing = 8.dp
    )
}