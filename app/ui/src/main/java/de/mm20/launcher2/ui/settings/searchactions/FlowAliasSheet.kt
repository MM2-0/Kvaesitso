package de.mm20.launcher2.ui.settings.searchactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
) {
    val id = rememberSaveable(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }
    var alias by rememberSaveable(id) { mutableStateOf(initial?.alias ?: "") }
    var targetKey by rememberSaveable(id) { mutableStateOf(initial?.targetKey ?: "") }
    val selected = targets.find { it.key == targetKey }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.flow_alias_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = alias, onValueChange = { alias = it }, enabled = !saving,
                label = { Text(stringResource(R.string.flow_alias_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                isError = error != null,
            )
            Text(selected?.let { "$alias → ${it.label}" } ?: stringResource(R.string.flow_alias_choose_target),
                style = MaterialTheme.typography.titleMedium)
            if (targetKey.isNotEmpty() && selected == null) {
                Text(stringResource(R.string.flow_alias_target_unavailable), color = MaterialTheme.colorScheme.error)
            }
            // Virtualized and height bounded, including large fonts and many installed apps.
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(targets, key = { it.key }) { target ->
                    TextButton(onClick = { targetKey = target.key }, enabled = !saving) {
                        RadioButton(selected = targetKey == target.key, onClick = null)
                        Text(target.label, Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial != null) TextButton(onClick = { onDelete(id) }, enabled = !saving) { Text(stringResource(R.string.menu_delete)) }
                TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) }
                Button(onClick = { onSave(FlowAlias(id, alias, targetKey)) }, enabled = !saving && selected != null) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
