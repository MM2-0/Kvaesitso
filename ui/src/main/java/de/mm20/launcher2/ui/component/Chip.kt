package de.mm20.launcher2.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Chip(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 8.dp),

        content = content
    )
}