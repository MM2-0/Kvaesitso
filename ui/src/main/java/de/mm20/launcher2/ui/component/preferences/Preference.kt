package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.ktx.conditional

@Composable
fun Preference(
    icon: ImageVector?,
    title: String,
    summary: String? = null,
    onClick: () -> Unit = {},
    controls: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    CompositionLocalProvider(
        LocalContentAlpha provides if (enabled) ContentAlpha.high else ContentAlpha.disabled
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .conditional(enabled, Modifier.clickable(onClick = onClick))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Box(
                    modifier = Modifier.width(56.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (icon != null) {
                        Icon(
                            modifier = Modifier.padding(start = 4.dp),
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                        )
                    }
                }
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
    }
}