package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import de.mm20.launcher2.ui.locals.LocalCardStyle
import kotlin.math.ln

@Composable
fun InnerCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    content: @Composable () -> Unit
) {
    val transition = updateTransition(raised, label = "InnerCard")

    val absoluteTonalElevation = LocalAbsoluteTonalElevation.current

    val elevation by transition.animateDp(label = "elevation", transitionSpec = {
        tween(250, if (targetState) 250 else 0)
    }) {
        if(it) 4.dp else 0.dp
    }

    val borderWidth by transition.animateDp(label = "borderWidth", transitionSpec = { tween(500) }) {
        if (it) 0.dp else 1.dp
    }
    val borderColor by transition.animateColor(label = "borderColor", transitionSpec = { tween(500) }) {
        MaterialTheme.colorScheme.outline.copy(alpha = if (it) 0f else 0.17f)
    }
    val bgAlpha by transition.animateFloat(label = "bgAlpha", transitionSpec = {
        tween(250, if (targetState) 0 else 250)
    }) {
        if (it) 1f else 0f
    }

    val shape = MaterialTheme.shapes.small

    val bgColor = MaterialTheme.colorScheme.surfaceColorAtElevation(absoluteTonalElevation + elevation)

    Box(
        modifier = modifier
            .border(BorderStroke(borderWidth, borderColor), shape)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .drawBehind {
                drawRect(
                    bgColor.copy(alpha = bgAlpha)
                )
            }
        ,
    ) {
        CompositionLocalProvider(
            LocalAbsoluteTonalElevation provides absoluteTonalElevation
        ) {
            content()
        }
    }
}

internal fun ColorScheme.surfaceColorAtElevation(
    elevation: Dp,
): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return surfaceTint.copy(alpha = alpha).compositeOver(surface)
}