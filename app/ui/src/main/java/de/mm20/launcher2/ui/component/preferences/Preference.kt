package de.mm20.launcher2.ui.component.preferences

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun Preference(
    title: @Composable (() -> Unit),
    summary: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {},
    controls: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(enabled = enabled, onClick = onClick)
            .background(containerColor)
            .padding(
                start = if (icon != null) 8.dp else 16.dp,
                end = 16.dp,
            )
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        } else {
            Box(modifier = Modifier.size(0.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.titleMedium) {
                title()
            }
            if (summary != null) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    summary()
                }
            }
        }
        if (controls != null) {
            Box(
                modifier = Modifier.padding(start = 24.dp)
            ) {
                controls()
            }
        }
    }
}

@Composable
fun Preference(
    title: String,
    icon: @Composable (() -> Unit),
    iconPadding: Boolean = true,
    summary: String? = null,
    onClick: () -> Unit = {},
    controls: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
) {
    Preference(
        title = {
            Text(text = title)
        },
        summary = if (summary != null) {
            {
                Text(text = summary)
            }
        } else null,
        icon = if (iconPadding) icon else null,
        onClick = onClick,
        controls = controls,
        enabled = enabled,
        containerColor = containerColor,
    )

}

@Composable
fun Preference(
    title: String,
    @DrawableRes icon: Int? = null,
    iconPadding: Boolean = icon != null,
    summary: String? = null,
    onClick: () -> Unit = {},
    controls: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
) {
    Preference(
        title,
        icon = {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }, iconPadding, summary, onClick, controls, enabled, containerColor,
    )
}