/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.ui.system

import de.mm20.launcher2.ui.component.TelosCyberHeading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.appmanagement.startup.ScriptIdentity
import de.mm20.launcher2.appmanagement.startup.StartupScript
import de.mm20.launcher2.appmanagement.startup.StartupScripts
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun StartupScriptsPreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scripts by StartupScripts.scripts.collectAsStateWithLifecycle()
    val runs by StartupScripts.runs.collectAsStateWithLifecycle()
    val running by StartupScripts.running.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var identity by rememberSaveable { mutableStateOf(ScriptIdentity.App.name) }
    var failed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { StartupScripts.load(context) }
    PreferenceCategory(title = stringResource(I18nR.string.startup_scripts_title)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(I18nR.string.startup_scripts_summary), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = scripts.size < 10, onClick = {
                name = ""; body = ""; identity = ScriptIdentity.App.name
                editing = UUID.randomUUID().toString()
            }) { Text(stringResource(I18nR.string.hc_add)) }
            if (failed) Text(stringResource(I18nR.string.hc_failed_try_again), color = MaterialTheme.colorScheme.error)
            for (script in scripts) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(script.name, style = MaterialTheme.typography.titleSmall)
                    Text(identityLabel(script.identity), style = MaterialTheme.typography.bodySmall)
                    SwitchPreference(
                        title = stringResource(I18nR.string.startup_script_enable),
                        value = script.enabled,
                        onValueChanged = { enabled -> scope.launch {
                            failed = runCatching { StartupScripts.save(context, script.copy(enabled = enabled)) }.isFailure
                        } },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = running == null, onClick = { scope.launch { StartupScripts.run(context, script.id) } }) {
                            Text(stringResource(I18nR.string.telos_shizuku_run))
                        }
                        OutlinedButton(onClick = { name = script.name; body = script.body; identity = script.identity.name; editing = script.id }) {
                            Text(stringResource(I18nR.string.hc_edit))
                        }
                        OutlinedButton(enabled = running != script.id, onClick = { scope.launch {
                            failed = runCatching { StartupScripts.remove(context, script.id) }.isFailure
                        } }) { Text(stringResource(I18nR.string.hc_delete)) }
                    }
                    if (running == script.id) Text(stringResource(I18nR.string.freeze_dashboard_running_now))
                    runs[script.id]?.let { run ->
                        Text(stringResource(I18nR.string.startup_script_exit, run.exitCode), style = MaterialTheme.typography.bodySmall)
                        if (run.output.isNotBlank()) Text(run.output, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    editing?.let { id ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.startup_scripts_title))  } },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text(stringResource(I18nR.string.hc_name_optional)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { if (it.length <= 16_384) body = it }, label = { Text(stringResource(I18nR.string.startup_script_code)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScriptIdentity.entries.forEach { mode ->
                            FilterChip(selected = identity == mode.name, onClick = { identity = mode.name }, label = { Text(identityLabel(mode)) })
                        }
                    }
                }
            },
            confirmButton = { Button(enabled = body.isNotBlank(), onClick = { scope.launch {
                // Editing disarms automatic execution; the user must explicitly enable the new body.
                val result = runCatching { StartupScripts.save(context, StartupScript(id, name.ifBlank { id.take(8) }, body, ScriptIdentity.valueOf(identity), false)) }
                failed = result.isFailure
                if (result.isSuccess) editing = null
            } }) { Text(stringResource(I18nR.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }
}

@Composable
private fun identityLabel(identity: ScriptIdentity): String = when (identity) {
    ScriptIdentity.App -> "Telos"
    ScriptIdentity.Shizuku -> "Shizuku"
    ScriptIdentity.Root -> stringResource(I18nR.string.au3_commsfreeze_backend_root)
}
