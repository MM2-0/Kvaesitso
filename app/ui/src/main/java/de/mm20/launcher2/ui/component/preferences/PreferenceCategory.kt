package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceCategory(
    title: String? = null,
    iconPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                de.mm20.launcher2.ui.component.TelosCyberSectionLabel(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier.clip(MaterialTheme.shapes.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}
