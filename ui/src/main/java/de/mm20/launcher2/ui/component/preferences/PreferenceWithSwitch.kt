package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceWithSwitch(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    switchValue: Boolean,
    onSwitchChanged: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = (Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Preference(
                title = title,
                summary = summary,
                icon = icon,
                enabled = enabled,
                onClick = onClick
            )
        }
        Box(
            modifier = Modifier
                .height(36.dp)
                .width(1.dp)
                .alpha(0.38f)
                .background(LocalContentColor.current)
        )
        Switch(
            modifier = Modifier.padding(horizontal = 16.dp),
            checked = switchValue,
            enabled = enabled,
            onCheckedChange = onSwitchChanged,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}