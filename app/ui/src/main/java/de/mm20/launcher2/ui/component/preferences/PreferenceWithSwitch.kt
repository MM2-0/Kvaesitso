package de.mm20.launcher2.ui.component.preferences

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@Composable
fun PreferenceWithSwitch(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    switchValue: Boolean,
    onSwitchChanged: (Boolean) -> Unit,
    iconPadding: Boolean = icon != null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Preference(
                title = title,
                summary = summary,
                icon = icon,
                enabled = enabled,
                onClick = onClick,
                iconPadding = iconPadding
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
            thumbContent = {
                Icon(
                    painterResource(if (switchValue) R.drawable.check_20px else R.drawable.close_20px),
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    contentDescription = null,
                )
            }
        )
    }
}