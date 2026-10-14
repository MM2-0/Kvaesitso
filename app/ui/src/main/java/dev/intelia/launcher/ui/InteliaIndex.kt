package dev.intelia.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import java.text.Normalizer

internal val inteliaSpaceLabels = listOf(R.string.intelia_space_all, R.string.intelia_space_daily,
    R.string.intelia_space_focus, R.string.intelia_space_travel, R.string.intelia_space_rest)

internal fun indexLetter(label: String): String = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
    .firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

/** Open typographic lanes, manually assigned spaces; no inferred usage tracking. */
@Composable
internal fun InteliaIndex(apps: List<Application>, spaces: Map<String, Int>, onLaunch: (Application) -> Unit, onHold: (Application) -> Unit) {
    var space by rememberSaveable { mutableIntStateOf(0) }
    val visible = remember(apps, spaces, space) { apps.filter { space == 0 || spaces[it.key] == space } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            inteliaSpaceLabels.forEachIndexed { index, label ->
                TextButton(onClick = { space = index }, modifier = Modifier.widthIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(label), fontWeight = if (space == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (space == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        if (space == index) HorizontalDivider(Modifier.width(18.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (visible.isEmpty()) Text(stringResource(R.string.intelia_space_empty), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(visible, key = { _, app -> app.key }) { index, app ->
                val label = app.labelOverride ?: app.label
                val letter = indexLetter(label)
                val first = index == 0 || indexLetter(visible[index - 1].let { it.labelOverride ?: it.label }) != letter
                Row(Modifier.fillMaxWidth().combinedClickable(role = androidx.compose.ui.semantics.Role.Button,
                    onLongClickLabel = stringResource(R.string.intelia_app_options), onClick = { onLaunch(app) }, onLongClick = { onHold(app) })
                    .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (first) letter else "", Modifier.width(38.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppIcon(app, Modifier.size(32.dp))
                    Text(label, Modifier.padding(start = 16.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                }
            }
        }
    }
}
