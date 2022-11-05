package de.mm20.launcher2.ui.launcher.searchbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.component.SearchActionIcon

@Composable
fun SearchBarActions(
    modifier: Modifier = Modifier,
    actions: List<SearchAction>,
    reverse: Boolean = false,
) {
    val context = LocalContext.current
    AnimatedVisibility(actions.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .height(48.dp)
                .padding(bottom = if (reverse) 4.dp else 12.dp, top = if (reverse) 12.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(actions) {
                AssistChip(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    onClick = {
                        it.start(context)
                    },
                    label = { Text(it.label) },
                    leadingIcon = {
                        SearchActionIcon(icon = it.icon, color = it.iconColor, customIcon = it.customIcon)
                    }
                    /*leadingIcon = {
                        val icon = it.icon
                        if (icon == null) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (it.color == 0) MaterialTheme.colorScheme.primary else Color(
                                    it.color
                                )
                            )
                        } else {
                            AsyncImage(
                                modifier = Modifier.size(24.dp),
                                model = File(icon),
                                contentDescription = null
                            )
                        }
                    }*/
                )
            }
        }
    }
}