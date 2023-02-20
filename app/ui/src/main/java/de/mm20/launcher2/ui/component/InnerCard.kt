package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ln

@Composable
fun InnerCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    highlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val transition = updateTransition(InnerCardStyle(raised, highlight), label = "InnerCard")

    val absoluteTonalElevation = LocalAbsoluteTonalElevation.current

    val elevation by transition.animateDp(label = "elevation", transitionSpec = {
        tween(250, if (targetState == InnerCardStyle.Raised) 250 else 0)
    }) {
        if (it == InnerCardStyle.Raised) 2.dp else 0.dp
    }

    val borderWidth by transition.animateDp(
        label = "borderWidth",
        transitionSpec = { tween(500) }) {
        when (it) {
            InnerCardStyle.Raised -> 0.dp
            InnerCardStyle.Highlighted -> 1.dp
            InnerCardStyle.Default -> 1.dp
        }
    }
    val borderColor by transition.animateColor(
        label = "borderColor",
        transitionSpec = { tween(500) }) {
        when (it) {
            InnerCardStyle.Raised -> Color.Transparent
            InnerCardStyle.Highlighted -> MaterialTheme.colorScheme.secondary
            InnerCardStyle.Default -> MaterialTheme.colorScheme.outlineVariant
        }
    }

    val bgColor by transition.animateColor(label = "bgColor", transitionSpec = {
        tween(250, if (targetState == InnerCardStyle.Raised) 0 else 250)
    }) {
        when (it) {
            InnerCardStyle.Highlighted -> {
                MaterialTheme.colorScheme.secondaryContainer
            }
            InnerCardStyle.Default -> MaterialTheme
                .colorScheme.surface.copy(alpha = 0f)
            else -> {
                MaterialTheme.colorScheme.surfaceColorAtElevation(absoluteTonalElevation + elevation)
            }
        }
    }

    val shape = MaterialTheme.shapes.small

    Box(
        modifier = modifier
            .border(BorderStroke(borderWidth, borderColor), shape)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .drawBehind {
                drawRect(
                    bgColor
                )
            },
    ) {
        CompositionLocalProvider(
            LocalAbsoluteTonalElevation provides absoluteTonalElevation
        ) {
            content()
        }
    }
}

internal enum class InnerCardStyle {
    Default,
    Highlighted,
    Raised,
}

internal fun InnerCardStyle(raised: Boolean, highlight: Boolean): InnerCardStyle {
    return when {
        raised -> InnerCardStyle.Raised
        highlight -> InnerCardStyle.Highlighted
        else -> InnerCardStyle.Default
    }
}

internal fun ColorScheme.surfaceColorAtElevation(
    elevation: Dp,
): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return surfaceTint.copy(alpha = alpha).compositeOver(surface)
}