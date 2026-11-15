package de.mm20.launcher2.ui.component.preferences

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.telosTechCard

@Composable
fun Preference(
    title: @Composable (() -> Unit),
    summary: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {},
    controls: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f),
    toggleValue: Boolean? = null,
    toggleRole: Role = Role.Switch,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked =
            controls != null &&
                de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy.stackPreference(
                    maxWidth.value,
                    androidx.compose.ui.platform.LocalDensity.current.fontScale,
                )
        val text: @Composable () -> Unit = {
            Column(Modifier.padding(vertical = de.mm20.launcher2.ui.layout.UiSpacing.Content)) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() }
                if (summary != null) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        summary()
                    }
                }
            }
        }
        CompositionLocalProvider(
            LocalContentColor provides androidx.compose.material3.contentColorFor(containerColor)
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .telosTechCard(
                        borderColor = TelosCyber.NeonCyan,
                        shape = MaterialTheme.shapes.small,
                        borderWidth = 0.8.dp,
                        alpha = if (enabled) 0.32f else 0.12f,
                        backgroundColor = containerColor,
                    )
                    .then(
                        if (toggleValue != null)
                            Modifier.toggleable(
                                value = toggleValue,
                                enabled = enabled,
                                role = toggleRole,
                                onValueChange = { onClick() },
                            )
                        else Modifier.clickable(enabled = enabled, onClick = onClick)
                    )
                    .heightIn(min = de.mm20.launcher2.ui.layout.UiSpacing.TouchTarget)
                    .padding(horizontal = de.mm20.launcher2.ui.layout.UiSpacing.Content)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Box(
                            Modifier.width(48.dp)
                                .padding(end = 8.dp)
                                .alpha(if (enabled) 1f else 0.38f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            icon()
                        }
                    }
                    Box(Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f)) { text() }
                    if (controls != null && !stacked) {
                        Box(Modifier.padding(start = 24.dp)) { controls() }
                    }
                }
                if (controls != null && stacked) {
                    Box(Modifier.align(Alignment.End).padding(bottom = 12.dp)) { controls() }
                }
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f),
    toggleValue: Boolean? = null,
    toggleRole: Role = Role.Switch,
) {
    Preference(
        title = {
            Text(text = title)
        },
        summary =
            if (summary != null) {
                {
                    Text(text = summary)
                }
            } else null,
        icon = if (iconPadding) icon else null,
        onClick = onClick,
        controls = controls,
        enabled = enabled,
        containerColor = containerColor,
        toggleValue = toggleValue,
        toggleRole = toggleRole,
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f),
    toggleValue: Boolean? = null,
    toggleRole: Role = Role.Switch,
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
        },
        iconPadding,
        summary,
        onClick,
        controls,
        enabled,
        containerColor,
        toggleValue,
        toggleRole,
    )
}
