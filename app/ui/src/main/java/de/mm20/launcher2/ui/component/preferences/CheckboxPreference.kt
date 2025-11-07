package de.mm20.launcher2.ui.component.preferences

import androidx.annotation.DrawableRes
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CheckboxPreference(
    title: String,
    @DrawableRes icon: Int? = null,
    iconPadding: Boolean = icon != null,
    summary: String? = null,
    value: Boolean,
    onValueChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
    checkboxColors: CheckboxColors = CheckboxDefaults.colors(),
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
                enabled = enabled, checked = value, onCheckedChange = onValueChanged, colors = checkboxColors
            )
        }
    )
}

@Composable
fun CheckboxPreference(
    title: String,
    icon: @Composable () -> Unit,
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