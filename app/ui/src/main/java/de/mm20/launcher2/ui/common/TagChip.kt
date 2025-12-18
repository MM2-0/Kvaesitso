package de.mm20.launcher2.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.icons.VectorLayer
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.R
import org.koin.compose.koinInject

@Composable
fun TagChip(
    modifier: Modifier = Modifier,
    tag: Tag,
    selected: Boolean = false,
    dragged: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    clearable: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small

    val transition = updateTransition(
        if (dragged) 2 else 0 + if (selected) 1 else 0
    )

    val backgroundColor by transition.animateColor(
        transitionSpec = {
            if (targetState == 0 && initialState >= 2) tween(100, 200) else tween(100, 0)
        }
    ) {
        when (it) {
            0 -> Color.Transparent
            2 -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    }
    val borderColor by transition.animateColor {
        if (it and 1 == 1) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth by transition.animateDp {
        if (it and 1 == 1) 0.dp else 1.dp
    }
    val textColor by transition.animateColor {
        if (it and 1 == 1) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconColor by transition.animateColor {
        if (it and 1 == 1) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
    }
    val elevation by transition.animateDp(
        transitionSpec = {
            if (targetState >= 2) tween(100, 200) else tween(100, 0)
        }
    ) {
        if (it >= 2) 8.dp else 0.dp
    }

    val iconService: IconService = koinInject()
    val iconSize = InputChipDefaults.AvatarSize.toPixels()

    val icon by remember(tag, iconSize) {
        iconService.getIcon(
            tag,
            iconSize.toInt()
        )
    }.collectAsState(null)


    Row(
        modifier = modifier
            .padding(vertical = 8.dp)
            .height(32.dp)
            .widthIn(min = 48.dp)
            .shadow(elevation, shape, true)
            .border(borderWidth, borderColor, shape)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val foregroundLayer = (icon as? StaticLauncherIcon)?.foregroundLayer
        AnimatedVisibility(!compact || foregroundLayer !is VectorLayer) {
            if (foregroundLayer is TextLayer) {
                Text(
                    text = foregroundLayer.text,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(FilterChipDefaults.IconSize),
                    textAlign = TextAlign.Center,
                )
            } else if (foregroundLayer !is VectorLayer) {
                ShapedLauncherIcon(
                    modifier = Modifier.padding(start = if(compact) 4.dp else 0.dp),
                    size = InputChipDefaults.AvatarSize,
                    icon = { icon },
                    shape = CircleShape,
                )
            } else if (!compact) {
                Icon(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(FilterChipDefaults.IconSize),
                    painter = painterResource(foregroundLayer.icon),
                    contentDescription = null,
                    tint = iconColor
                )
            }
        }
        AnimatedVisibility(!compact || foregroundLayer is VectorLayer) {
            Text(
                tag.tag,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier.padding(start = if (compact) 12.dp else 8.dp, end = 8.dp)
            )
        }
        if (clearable) {
            Icon(
                modifier = Modifier
                    .clickable {
                        onClear?.invoke()
                    }
                    .size(FilterChipDefaults.IconSize),
                painter = painterResource(R.drawable.close_20px),
                contentDescription = null,
            )
        }
    }
}
