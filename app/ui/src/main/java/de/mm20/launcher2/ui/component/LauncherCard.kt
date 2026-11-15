package de.mm20.launcher2.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.theme.LocalOnWallpaper
import de.mm20.launcher2.ui.theme.chooseContrast
import de.mm20.launcher2.ui.theme.relativeLuminance
import de.mm20.launcher2.ui.theme.transparency.transparency

private val LocalSurfaceRange = staticCompositionLocalOf { Color.Black to Color.White }

@Composable
fun LauncherCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,
    backgroundOpacity: Float = MaterialTheme.transparency.surface,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
    contentColor: Color? = null,
    frosted: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    val behind = LocalSurfaceRange.current
    val range = remember(color, behind) { color.compositeOver(behind.first) to color.compositeOver(behind.second) }
    val resolvedContent = remember(range, contentColor) {
        contentColor ?: run {
            val a = relativeLuminance(range.first.toArgb())
            val b = relativeLuminance(range.second.toArgb())
            if (chooseContrast(minOf(a, b), maxOf(a, b)).darkText) Color.Black else Color.White
        }
    }
    // Both inherited content and explicit onSurface usages follow the final composited surface.
    val base = MaterialTheme.colorScheme
    val colors = remember(base, resolvedContent) {
        base.copy(onSurface = resolvedContent, onSurfaceVariant = resolvedContent,
            onBackground = resolvedContent)
    }
    CompositionLocalProvider(LocalOnWallpaper provides false, LocalSurfaceRange provides range) {
        MaterialTheme(colorScheme = colors) {
            Surface(
                modifier = if (frosted) modifier.frostedGlass(color, shape) else modifier,
                shape = shape,
                border = if (color.alpha > 0f) androidx.compose.foundation.BorderStroke(
                    1.dp, TelosCyber.NeonCyan.copy(alpha = 0.48f),
                ) else null,
                content = content,
                contentColor = resolvedContent,
                color = if (frosted) Color.Transparent else color,
                shadowElevation = if (frosted) maxOf(1.dp, elevation) else if (color.alpha == 1f) elevation else 0.dp,
                // Avoid a hidden tonal tint changing the surface after contrast was computed.
                tonalElevation = 0.dp,
            )
        }
    }
}
