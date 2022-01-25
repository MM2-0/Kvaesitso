package de.mm20.launcher2.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LauncherCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,
    backgroundOpacity: Float = 1f,
    content: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        content = content,
        color = MaterialTheme.colorScheme.surface.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
        shadowElevation = elevation,
        tonalElevation = elevation
    )
}