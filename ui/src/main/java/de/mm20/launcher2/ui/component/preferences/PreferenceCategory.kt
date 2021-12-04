package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun PreferenceCategory(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shadowElevation = 2.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth()
    ) {
        Column {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 56.dp),
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            content()
        }
    }
}