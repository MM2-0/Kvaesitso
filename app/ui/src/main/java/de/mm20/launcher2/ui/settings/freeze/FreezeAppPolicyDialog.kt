package de.mm20.launcher2.ui.settings.freeze

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.os.Process
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.preferences.*

@Composable
internal fun FreezeAppPolicyDialog(app: Application, vm: FreezeSettingsScreenVM, close: () -> Unit) {
    FreezePackagePolicyDialog(app.componentName.packageName, app.label, app.user, vm, close)
}

@Composable
internal fun FreezePackagePolicyDialog(pkg: String, label: String, user: android.os.UserHandle,
    vm: FreezeSettingsScreenVM, close: () -> Unit) {
    val currentUser = user == Process.myUserHandle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val never by vm.neverFreezeApps.collectAsStateWithLifecycle()
    val methods by vm.freezeMethods.collectAsStateWithLifecycle()
    val delays by vm.screenOffDelays.collectAsStateWithLifecycle()
    val enabled by vm.companionEnabled.collectAsStateWithLifecycle()
    val rules by vm.companionExtras.collectAsStateWithLifecycle()
    val choices by vm.companionChoices.collectAsStateWithLifecycle()
    val descriptions by vm.bloatDescriptions.collectAsStateWithLifecycle()
    val approved by vm.riskApproved.collectAsStateWithLifecycle()
    val selected = remember(pkg, rules, choices) {
        if (pkg in rules) rules[pkg].orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        else if (pkg == "com.google.android.apps.bard" && choices.any { it.first == "com.google.android.googlequicksearchbox" })
            setOf("com.google.android.googlequicksearchbox") else emptySet()
    }
    var picker by rememberSaveable(pkg) { mutableStateOf(false) }
    val state = appFreezeState(pkg, candidates, never)
    val method = appFreezeMethod(pkg, methods)
    val description = descriptions[pkg]
    AlertDialog(onDismissRequest = close, title = { TelosCyberHeading {  Text(label)  } },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(android.R.string.ok)) } },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pkg, style = MaterialTheme.typography.bodySmall)
                if (!currentUser) Text(stringResource(I18nR.string.freeze_hub_profile_notice))
                ListPreference(title = stringResource(I18nR.string.preference_freeze_auto_category),
                    value = if (state == AppFreezeState.Candidate) (if (method == FreezeMethod.Disable) 2 else 1) else if (state == AppFreezeState.NeverFreeze) 3 else 0,
                    items = listOf(I18nR.string.freeze_app_state_none, I18nR.string.freeze_app_state_candidate_suspend,
                        I18nR.string.freeze_app_state_candidate_disable, I18nR.string.freeze_app_state_never).mapIndexed { i, res -> stringResource(res) to i },
                    enabled = currentUser,
                    onValueChanged = { choice -> if (choice != null) vm.setPackageFreezeState(pkg, user,
                        when (choice) { 1, 2 -> AppFreezeState.Candidate; 3 -> AppFreezeState.NeverFreeze; else -> AppFreezeState.None },
                        if (choice == 2) FreezeMethod.Disable else FreezeMethod.Suspend) })
                Text(stringResource(I18nR.string.freeze_hub_delay_help), style = MaterialTheme.typography.bodySmall)
                ListPreference(title = stringResource(I18nR.string.freeze_hub_delay), value = delays[pkg] ?: -1,
                    enabled = currentUser,
                    items = listOf(stringResource(I18nR.string.freeze_hub_inherit) to -1,
                        stringResource(I18nR.string.app_lock_grace_period_immediately) to 0) +
                        listOf(1, 5, 10, 15, 30, 60, 120).map { stringResource(I18nR.string.au3_commsfreeze_minutes, it) to it },
                    onValueChanged = { choice -> if (choice != null) vm.setDelay(pkg, choice.takeIf { it >= 0 }) })
                Text(stringResource(I18nR.string.freeze_hub_companion_help), style = MaterialTheme.typography.bodySmall)
                SwitchPreference(title = stringResource(I18nR.string.freeze_hub_companions), value = pkg in enabled,
                    enabled = currentUser && selected.isNotEmpty(),
                    onValueChanged = { vm.setCompanionRule(pkg, it, selected) })
                Preference(title = stringResource(I18nR.string.freeze_hub_companions),
                    summary = selected.joinToString("\n") { dep -> choices.firstOrNull { it.first == dep }?.second ?: dep },
                    enabled = currentUser, onClick = { picker = true })
                if (pkg == "com.google.android.apps.bard" && selected.contains("com.google.android.googlequicksearchbox")) {
                    Text("${label} → ${choices.firstOrNull { it.first == "com.google.android.googlequicksearchbox" }?.second.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text("UAD-NG · ${description?.recommendation.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                Text(description?.description?.takeIf { it.isNotBlank() } ?: stringResource(I18nR.string.freeze_hub_unknown),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(I18nR.string.freeze_hub_uad_help), style = MaterialTheme.typography.bodySmall)
                if (description?.recommendation.equals("Expert", true)) {
                    SwitchPreference(title = stringResource(I18nR.string.freeze_hub_expert), value = pkg in approved,
                        enabled = currentUser, onValueChanged = { vm.setRiskApproved(pkg, it) })
                }
            }
        })
    if (picker) CompanionPicker(choices.filter { it.first != pkg }, selected,
        save = { vm.setCompanionRule(pkg, pkg in enabled && it.isNotEmpty(), it); picker = false },
        close = { picker = false })
}

@Composable
private fun CompanionPicker(choices: List<Pair<String, String>>, initial: Set<String>, save: (Set<String>) -> Unit, close: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf(initial.toList()) }
    val shown = remember(choices, query) { choices.filter { it.first.contains(query, true) || it.second.contains(query, true) } }
    AlertDialog(onDismissRequest = close, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.freeze_hub_companions))  } },
        confirmButton = { TextButton(onClick = { save(chosen.toSet()) }) { Text(stringResource(I18nR.string.hc_apply)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column {
                TelosSearchBar(value = query, onValueChange = { query = it }, placeholder = stringResource(I18nR.string.hf_freeze_search_apps))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(shown, key = { it.first }) { (pkg, label) ->
                        CheckboxPreference(title = label, summary = pkg, value = pkg in chosen,
                            onValueChanged = { checked -> chosen = if (checked) (chosen + pkg).distinct() else chosen - pkg })
                    }
                }
            }
        })
}
