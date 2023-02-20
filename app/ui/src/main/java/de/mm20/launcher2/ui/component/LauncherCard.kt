package de.mm20.launcher2.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.locals.LocalCardStyle

@Composable
fun LauncherCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,
    backgroundOpacity: Float = LocalCardStyle.current.opacity,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
    border: BorderStroke? = LocalCardStyle.current.borderWidth.takeIf { it > 0 }
        ?.let { BorderStroke(it.dp, MaterialTheme.colorScheme.surface) },
    content: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = shape,
        border = border,
        content = content,
        contentColor = MaterialTheme.colorScheme.onSurface,
        color = color,
        shadowElevation = if (backgroundOpacity == 1f) elevation else 0.dp,
        tonalElevation = elevation,
    )
}

@Composable
fun PartialLauncherCard(
    modifier: Modifier = Modifier,
    isTop: Boolean = false,
    isBottom: Boolean = false,
    elevation: Dp = 2.dp,
    backgroundOpacity: Float = LocalCardStyle.current.opacity,
    content: @Composable () -> Unit
) {

    if (isTop && isBottom) {
        LauncherCard(modifier = modifier, content = content)
    } else if (!isTop && !isBottom) {
        CardMiddlePiece(modifier = modifier, elevation = elevation, content = content)
    } else {
        CardEndPiece(
            modifier = modifier,
            isTop = isTop,
            isBottom = isBottom,
            elevation = elevation,
            backgroundOpacity = backgroundOpacity,
            content = content
        )
    }
}

@Composable
private fun CardMiddlePiece(
    modifier: Modifier,
    elevation: Dp,
    backgroundOpacity: Float = LocalCardStyle.current.opacity,
    content: @Composable () -> Unit
) {
    val borderWidth = LocalCardStyle.current.borderWidth.dp
    val borderColor = MaterialTheme.colorScheme.surface

    val absoluteElevation = LocalAbsoluteTonalElevation.current + elevation
    Box(
        modifier = modifier
            .shadow(if (backgroundOpacity < 1f) 0.dp else elevation, RectangleShape, true)
            .background(
                if (backgroundOpacity == 1f) {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(absoluteElevation)
                } else {
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = backgroundOpacity.coerceIn(
                            0f,
                            1f
                        )
                    )
                }
            )
            .drawBehind {
                if (borderWidth == 0.dp) return@drawBehind
                val border = borderWidth.toPx()
                drawRect(
                    color = borderColor,
                    topLeft = Offset.Zero,
                    size = size.copy(width = border)
                )
                drawRect(
                    color = borderColor,
                    topLeft = Offset(size.width - border, 0f),
                    size = size.copy(width = border)
                )
            },
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalAbsoluteTonalElevation provides absoluteElevation,
        ) {
            content()
        }
    }
}

@Composable
private fun CardEndPiece(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    isBottom: Boolean,
    elevation: Dp,
    backgroundOpacity: Float,
    content: @Composable () -> Unit,
) {
    val shape = when {
        isTop -> MaterialTheme.shapes.medium.copy(
            bottomEnd = CornerSize(0),
            bottomStart = CornerSize(0),
        )
        isBottom -> MaterialTheme.shapes.medium.copy(
            topEnd = CornerSize(0),
            topStart = CornerSize(0),
        )
        else -> RectangleShape
    }

    val borderWidth = LocalCardStyle.current.borderWidth.dp
    val borderColor = MaterialTheme.colorScheme.surface

    val absoluteElevation = LocalAbsoluteTonalElevation.current + elevation
    Box(
        modifier = modifier
            .shadow(if (backgroundOpacity < 1f) 0.dp else elevation, shape, true)
            .background(
                if (backgroundOpacity == 1f) {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(absoluteElevation)
                } else {
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = backgroundOpacity.coerceIn(
                            0f,
                            1f
                        )
                    )
                }
            )
            .drawWithCache {
                val border = borderWidth.toPx()
                val outline = shape.createOutline(
                    size.copy(height = size.height + border),
                    layoutDirection,
                    Density(density, fontScale)
                )
                onDrawBehind {
                    if (borderWidth == 0.dp) return@onDrawBehind
                    withTransform({
                        translate(0f, if (isBottom) -border else 0f)
                    }) {
                        drawOutline(
                            outline,
                            borderColor,
                            style = Stroke(width = border * 2)
                        )
                    }
                }
            },
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalAbsoluteTonalElevation provides absoluteElevation,
        ) {
            content()
        }
    }
}