package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun Preference(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: () -> Unit = {},
    controls: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(end = 24.dp),
            tint = MaterialTheme.colors.primary
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle2)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = 1.dp)
                )
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