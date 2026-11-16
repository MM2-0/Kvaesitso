package de.mm20.launcher2.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api

import de.mm20.launcher2.base.R as BaseR
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact Cyber-Tech command strip: search stays an icon until opened, filters and
 * bulk actions are 48dp icons with long-press labels, optional HUD status line.
 */
data class TelosHudAction(
    val id: String,
    val icon: Int,
    val label: String,
    val selected: Boolean = false,
    val badge: String? = null,
    val accent: Color = TelosCyber.NeonCyan,
    val onClick: () -> Unit,
)

@Composable
fun TelosCommandHud(
    actions: List<TelosHudAction>,
    modifier: Modifier = Modifier,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    searchPlaceholder: String = "",
    status: String? = null,
) {
    var searchOpen by rememberSaveable { mutableStateOf(!searchQuery.isNullOrBlank()) }
    val searching = searchOpen || !searchQuery.isNullOrBlank()
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (onSearchQueryChange != null && searchQuery != null) {
            AnimatedVisibility(
                visible = searching,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                TelosSearchBar(searchQuery, onSearchQueryChange, searchPlaceholder)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .telosTechCard(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(18.dp), alpha = 0.7f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onSearchQueryChange != null) {
                TelosHudIcon(
                    selected = searching,
                    onClick = {
                        searchOpen = !searching
                        if (searching) onSearchQueryChange("")
                    },
                    icon = BaseR.drawable.search_24px,
                    label = searchPlaceholder,
                    accent = TelosCyber.NeonCyan,
                )
            }
            actions.forEach { action ->
                val button = @Composable {
                    TelosHudIcon(
                        selected = action.selected,
                        onClick = action.onClick,
                        icon = action.icon,
                        label = action.label,
                        accent = action.accent,
                    )
                }
                val badge = action.badge
                if (badge.isNullOrBlank()) button()
                else BadgedBox(
                    badge = {
                        Badge(containerColor = action.accent, contentColor = MaterialTheme.colorScheme.onPrimary) {
                            Text(badge, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) { button() }
            }
        }
        if (!status.isNullOrBlank()) {
            TelosDotMatrixText(
                text = status,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelosHudIcon(
    selected: Boolean,
    onClick: () -> Unit,
    icon: Int,
    label: String,
    accent: Color = TelosCyber.NeonCyan,
) {
    Tooltip(tooltipText = label) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                },
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
