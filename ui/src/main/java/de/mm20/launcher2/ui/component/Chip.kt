package de.mm20.launcher2.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Chip(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.labelLarge,
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                content()
            }
        }
    }
}

@Composable
fun Chip(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit = {},
    icon: Painter? = null,
    rightIcon: ImageVector? = null,
    rightAction: (() -> Unit)? = null
) {
    Chip(
        modifier = modifier.width(IntrinsicSize.Max),
        onClick = onClick
    ) {
        if (icon != null) {
            Image(
                modifier = Modifier.padding(horizontal = 6.dp).size(20.dp),
                painter = icon,
                contentDescription = null
            )
        }
        Text(
            modifier = Modifier
                .weight(1f, false)
                .padding(
                start = if (icon == null) 12.dp else 4.dp,
                end = if (rightIcon == null) 12.dp else 4.dp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            text = text)
        if (rightIcon != null) {
            if (rightAction != null) {
                IconButton(
                    modifier = Modifier.size(32.dp),
                    onClick = rightAction
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = rightIcon,
                        contentDescription = null
                    )
                }
            } else {
                Icon(
                    modifier = Modifier.padding(horizontal = 6.dp).size(20.dp),
                    imageVector = rightIcon,
                    contentDescription = null
                )
            }
        }
    }
}