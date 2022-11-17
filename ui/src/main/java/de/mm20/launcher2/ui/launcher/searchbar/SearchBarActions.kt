package de.mm20.launcher2.ui.launcher.searchbar

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.component.SearchActionIcon
import de.mm20.launcher2.ui.settings.SettingsActivity

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
                .padding(bottom = if (reverse) 0.dp else 8.dp, top = if (reverse) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = 8.dp, end = 4.dp)
        ) {
            items(actions) {
                AssistChip(
                    modifier = Modifier.padding(4.dp),
                    onClick = {
                        it.start(context)
                    },
                    label = { Text(it.label) },
                    leadingIcon = {
                        SearchActionIcon(
                            action = it
                        )
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
            item {
                SmallFloatingActionButton(
                    modifier = Modifier.padding(start = 4.dp),
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    onClick = {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java).apply {
                                putExtra(SettingsActivity.EXTRA_ROUTE, "settings/search/searchactions")
                            }
                        )
                    }
                ) {

                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = null)
                }
            }
        }
    }
}