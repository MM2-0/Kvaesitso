package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.locals.LocalCardStyle

@Composable
fun InnerCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    content: @Composable () -> Unit
) {
    val transition = updateTransition(raised, label = "InnerCard")

    val elevation by transition.animateDp(label = "elevation", transitionSpec = {
        tween(250, if (targetState) 250 else 0)
    }) {
        if(it) 4.dp else 0.dp
    }

    val borderWidth by transition.animateDp(label = "borderWidth", transitionSpec = { tween(500) }) {
        if (it) 0.dp else 1.dp
    }
    val borderColor by transition.animateColor(label = "borderColor", transitionSpec = { tween(500) }) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (it) 0f else 1f)
    }

    Surface(
        shadowElevation = elevation,
        tonalElevation = elevation,
        shape = RoundedCornerShape(LocalCardStyle.current.radius.dp),
        border = BorderStroke(borderWidth, borderColor),
        content = content,
        modifier = modifier
    )
}