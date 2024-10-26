package de.mm20.launcher2.ui.common

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Tag
import de.mm20.launcher2.ui.ktx.splitLeadingEmoji

@Composable
fun TagChip(
    modifier: Modifier = Modifier,
    tag: Tag,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    clearable: Boolean = false,
    onClear: (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
) {
    val (emoji, tagName) = remember(tag.tag) {
        tag.tag.splitLeadingEmoji()
    }

    val shape = MaterialTheme.shapes.small

    val transition = updateTransition(selected)

    val backgroundColor by transition.animateColor {
        if (it) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor by transition.animateColor {
        if (it) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth by transition.animateDp {
        if (it) 0.dp else 1.dp
    }
    val textColor by transition.animateColor {
        if (it) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconColor by transition.animateColor {
        if (it) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
    }


    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape)
            .clip(shape)
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

    /*FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            if (emoji != null && tagName != null) {
                Text(
                    emoji,
                    modifier = Modifier.width(FilterChipDefaults.IconSize),
                    textAlign = TextAlign.Center,
                )
            } else if (tagName != null) {
                Icon(
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                    imageVector = Icons.Rounded.Tag,
                    contentDescription = null
                )
            }
        },
        label = {
            Text(
                tagName ?: emoji ?: "",
            )
        },
        colors = colors,
        elevation = elevation,
        trailingIcon = if (clearable) {
            {
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
        } else null
    )*/
}