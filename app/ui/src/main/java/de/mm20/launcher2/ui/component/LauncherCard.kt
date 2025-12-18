package de.mm20.launcher2.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.theme.transparency.transparency

@Composable
fun LauncherCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,
    backgroundOpacity: Float = MaterialTheme.transparency.surface,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(
        alpha = backgroundOpacity.coerceIn(
            0f,
            1f
        )
    ),
    content: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = shape,
        content = content,
        contentColor = MaterialTheme.colorScheme.onSurface,
        color = color,
        shadowElevation = if (backgroundOpacity == 1f) elevation else 0.dp,
        tonalElevation = elevation,
    )
}