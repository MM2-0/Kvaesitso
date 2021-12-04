package de.mm20.launcher2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.theme.divider

@Composable
fun InformationText(
        text: String,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null
) {
    Card(
            elevation = 0.dp,
            border = BorderStroke(
                    width = 1.dp,
                    color = LocalContentColor.current.copy(alpha = ContentAlpha.divider)),
            modifier = modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp)
            )
        }
    }
}