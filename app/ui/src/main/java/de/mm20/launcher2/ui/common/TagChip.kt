package de.mm20.launcher2.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.mm20.launcher2.search.data.Tag
import de.mm20.launcher2.ui.ktx.splitLeadingEmoji

@Composable
fun TagChip(
    modifier: Modifier = Modifier,
    tag: Tag,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    clearable: Boolean = false,
    onClear: (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
) {
    val (emoji, tagName) = remember(tag.tag) {
        tag.tag.splitLeadingEmoji()
    }

    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            if (emoji != null && tagName != null) {
                Text(
                    emoji,
                    modifier = Modifier.width(FilterChipDefaults.IconSize),
                    textAlign = TextAlign.Center,
                )
            } else if (tagName != null) {
                Icon(
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                    imageVector = Icons.Rounded.Tag,
                    contentDescription = null
                )
            }
        },
        label = {
            Text(
                tagName ?: emoji ?: "",
            )
        },
        colors = colors,
        elevation = elevation,
        trailingIcon = if (clearable) {
            {
                Icon(
                    modifier = Modifier.clickable {
                        onClear?.invoke()
                    }.size(FilterChipDefaults.IconSize),
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                )
            }
        } else null
    )
}