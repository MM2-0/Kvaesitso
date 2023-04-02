package de.mm20.launcher2.ui.common

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.data.Tag

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
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Tag,
                contentDescription = null
            )
        },
        label = {
            Text(
                tag.label
            )
        },
        colors = colors,
        elevation = elevation,
        trailingIcon = if (clearable) {
            {
                Icon(
                    modifier = Modifier.clickable {
                        onClear?.invoke()
                    },
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null
                )
            }
        } else null
    )
}