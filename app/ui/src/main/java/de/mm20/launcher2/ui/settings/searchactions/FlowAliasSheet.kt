package de.mm20.launcher2.ui.settings.searchactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.searchactions.flow.FlowAlias
import de.mm20.launcher2.searchactions.flow.FlowAliasTarget
import de.mm20.launcher2.ui.R
import java.util.UUID

/** A focused editor avoids changing the existing arbitrary-intent customization flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowAliasSheet(
    initial: FlowAlias?,
    targets: List<FlowAliasTarget>,
    error: String?,
    saving: Boolean,
    onSave: (FlowAlias) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    targetIcon: (@Composable (FlowAliasTarget) -> Unit)? = null,
) {
    val id = rememberSaveable(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }
    var alias by rememberSaveable(id) { mutableStateOf(initial?.alias ?: "") }
    var targetKeys by rememberSaveable(id) { mutableStateOf(initial?.targetKeys ?: emptyList<String>()) }
    val selected = targetKeys.map { key -> targets.find { it.key == key } }
    var targetQuery by rememberSaveable(id) { mutableStateOf("") }
    val filteredTargets = remember(targets, targetQuery) {
        val normalized = de.mm20.launcher2.searchactions.flow.FlowAliases.normalize(targetQuery)
        targets.filter { de.mm20.launcher2.searchactions.flow.FlowAliases.normalize(it.label).contains(normalized) }
    }
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = { if (!saving) onDismiss() },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.flow_alias_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = alias, onValueChange = { alias = it }, enabled = !saving,
                label = { Text(stringResource(R.string.flow_alias_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                isError = error != null,
            )
            Text(stringResource(R.string.intelia_flow_order), style = MaterialTheme.typography.bodyMedium)
            selected.forEachIndexed { index, target ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${index + 1}", Modifier.width(26.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (target != null) targetIcon?.invoke(target)
                    Text(target?.label ?: stringResource(R.string.flow_alias_target_unavailable), Modifier.weight(1f))
                    if (index > 0) TextButton(enabled = !saving, onClick = {
                        targetKeys = targetKeys.toMutableList().apply { val value = removeAt(index); add(index - 1, value) }
                    }) { Text("↑", Modifier.semantics { contentDescription = "${index + 1} → $index" }) }
                    TextButton(enabled = !saving, onClick = { targetKeys = targetKeys.filterIndexed { position, _ -> position != index } }) {
                        Text(stringResource(R.string.intelia_remove))
                    }
                }
            }
            // Virtualized and height bounded, including large fonts and many installed apps.
            OutlinedTextField(
                value = targetQuery, onValueChange = { targetQuery = it }, singleLine = true,
                label = { Text(stringResource(R.string.intelia_targets_search)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !saving,
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(filteredTargets, key = { it.key }) { target ->
                    TextButton(onClick = { targetKeys = if (target.key in targetKeys) targetKeys - target.key else targetKeys + target.key },
                        enabled = !saving && (target.key in targetKeys || targetKeys.size < 4)) {
                        Checkbox(checked = target.key in targetKeys, onCheckedChange = null)
                        targetIcon?.invoke(target)
                        Text(target.label, Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial != null) TextButton(onClick = { onDelete(id) }, enabled = !saving) { Text(stringResource(R.string.menu_delete)) }
                TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) }
                Button(onClick = { onSave(FlowAlias(id, alias, targetKeys.first(), targetKeys.drop(1))) }, enabled = !saving && selected.isNotEmpty() && selected.none { it == null }) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
