package de.mm20.launcher2.ui.common

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Tag
import de.mm20.launcher2.ui.ktx.splitLeadingEmoji

@Composable
fun TagChip(
    modifier: Modifier = Modifier,
    tag: Tag,
    selected: Boolean = false,
    dragged: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    clearable: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    val (emoji, tagName) = remember(tag.tag) {
        tag.tag.splitLeadingEmoji()
    }

    val shape = MaterialTheme.shapes.small

    val transition = updateTransition(
        if (dragged) 2 else 0 + if (selected) 1 else 0
    )

    val backgroundColor by transition.animateColor(
        transitionSpec = {
            if (targetState == 0) tween(100, 200) else tween(100, 0)
        }
    ) {
        when(it) {
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
            if (targetState >=2) tween(100, 200) else tween(100, 0)
        }
    ) {
        if (it >= 2) 8.dp else 0.dp
    }


    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .shadow(elevation, shape, true)
            .border(borderWidth, borderColor, shape)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null && tagName != null) {
            Text(
                emoji,
                modifier = Modifier.width(FilterChipDefaults.IconSize),
                textAlign = TextAlign.Center,
            )
        } else {
            Icon(
                modifier = Modifier
                    .size(FilterChipDefaults.IconSize),
                imageVector = Icons.Rounded.Tag,
                contentDescription = null,
                tint = iconColor
            )
        }
        Text(
            tagName ?: emoji ?: "",
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (clearable) {
            Icon(
                modifier = Modifier
                    .clickable {
                        onClear?.invoke()
                    }
                    .size(FilterChipDefaults.IconSize),
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
            )
        }
    }
}
