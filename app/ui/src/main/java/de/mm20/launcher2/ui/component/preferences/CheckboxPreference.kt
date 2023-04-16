package de.mm20.launcher2.ui.component.preferences

import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CheckboxPreference(
    title: String,
    icon: ImageVector? = null,
    iconPadding: Boolean = true,
    summary: String? = null,
    value: Boolean,
    onValueChanged: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Preference(
        title = title,
        icon = icon,
        iconPadding = iconPadding,
        summary = summary,
        enabled = enabled,
        onClick = {
            onValueChanged(!value)
        },
        controls = {
            Checkbox(
                enabled = enabled, checked = value, onCheckedChange = onValueChanged,
            )
        }
    )
}