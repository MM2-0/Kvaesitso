package de.mm20.launcher2.ui.network.blocklists

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.Blocklist
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.BlocklistGroup
import de.mm20.launcher2.network.api.BlocklistUpdateState
import de.mm20.launcher2.network.api.RuleScope
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.network.firewall.AddRuleDialog
import de.mm20.launcher2.ui.network.firewall.formatDateTime
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.text.NumberFormat

private typealias IconsNetworkBlocklistsScreen = de.mm20.launcher2.base.R.drawable

@Serializable
data object NetworkBlocklistsRoute : NavKey

/** The Rethink DNS blocklists: download, switch lists on and off, and the domains the user allows anyway. */
@Composable
fun NetworkBlocklistsScreen() {
    val blocklists: BlocklistController = koinInject()
    val directory: AppDirectory = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val groups by blocklists.groups.collectAsState()
    val enabled by blocklists.enabled.collectAsState()
    val installed by blocklists.installed.collectAsState()
    val state by blocklists.updateState.collectAsState()
    val lastUpdated by blocklists.lastUpdatedMs.collectAsState()
    val updateAvailable by blocklists.updateAvailable.collectAsState()
    val auto by blocklists.autoUpdate.collectAsState()
    val storage by blocklists.storageBytes.collectAsState()
    val counts by blocklists.blockCounts.collectAsState()
    val bypass by blocklists.bypass.collectAsState()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var addBypass by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf<Boolean?>(null) }
    val number = remember { NumberFormat.getInstance() }
    var bq by rememberSaveable { mutableStateOf("") }
    val groupNames = groups.associate { it.id to groupName(it) }
    val TS = de.mm20.launcher2.comms.search.TelosSearch
    fun groupHit(g: BlocklistGroup) = bq.isBlank() || TS.matches(bq, groupNames[g.id], g.name, g.description)
    fun listHit(g: BlocklistGroup, l: Blocklist) = bq.isBlank() || groupHit(g) || TS.matches(bq, l.name, l.description, l.url)
    val shownBypass = if (bq.isBlank()) bypass else bypass.filter { b ->
        TS.matches(bq, b.domain, (b.scope as? RuleScope.App)?.let { directory.labelFor(it.appId) })
    }

    PreferenceScreen(title = stringResource(I18nR.string.netfw_b_title)) {
        item {
            Text(
                stringResource(I18nR.string.netfw_b_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            PreferenceCategory {
                val busy = state is BlocklistUpdateState.Downloading
                val summary = when (val st = state) {
                    is BlocklistUpdateState.Downloading ->
                        if (st.progress < 0f) stringResource(I18nR.string.netfw_b_updating)
                        else stringResource(I18nR.string.netfw_b_updating_pct, (st.progress * 100).toInt())
                    is BlocklistUpdateState.Failed -> stringResource(I18nR.string.netfw_b_failed, st.message)
                    BlocklistUpdateState.Idle -> when {
                        !installed -> stringResource(I18nR.string.netfw_b_not_installed)
                        updateAvailable -> stringResource(I18nR.string.netfw_b_update_available)
                        checked == false -> stringResource(I18nR.string.netfw_b_up_to_date)
                        lastUpdated > 0 -> stringResource(I18nR.string.netfw_b_updated, formatDateTime(context, lastUpdated))
                        else -> stringResource(I18nR.string.netfw_b_never)
                    }
                }
                Preference(
                    title = stringResource(
                        when {
                            !installed -> I18nR.string.netfw_b_download
                            updateAvailable -> I18nR.string.netfw_b_update_now
                            else -> I18nR.string.netfw_b_check
                        }
                    ),
                    summary = summary,
                    icon = IconsNetworkBlocklistsScreen.download_24px,
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            if (!installed || updateAvailable) {
                                blocklists.update(force = false)
                            } else {
                                val check = blocklists.checkForUpdate()
                                checked = check.getOrNull()
                                check.exceptionOrNull()?.let { e ->
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(I18nR.string.netfw_b_failed, e.message ?: e.javaClass.simpleName),
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                )
                (state as? BlocklistUpdateState.Downloading)?.let { st ->
                    if (st.progress >= 0f) {
                        LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
                if (installed) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.netfw_b_auto),
                        summary = stringResource(I18nR.string.netfw_b_auto_summary),
                        icon = IconsNetworkBlocklistsScreen.schedule_24px,
                        value = auto,
                        onValueChanged = { v -> scope.launch { blocklists.setAutoUpdate(v) } },
                    )
                    Preference(
                        title = stringResource(I18nR.string.netfw_b_remove),
                        summary = stringResource(I18nR.string.netfw_b_storage, Formatter.formatShortFileSize(context, storage)),
                        icon = IconsNetworkBlocklistsScreen.delete_24px,
                        onClick = { scope.launch { blocklists.removeDownloaded() } },
                    )
                }
            }
        }
        if (installed) item {
            de.mm20.launcher2.ui.component.TelosSearchBar(bq, { bq = it }, stringResource(I18nR.string.hc_search))
        }
        if (installed) {
            val sections = listOf(
                "parentalcontrol" to I18nR.string.netfw_b_sec_parental,
                "security" to I18nR.string.netfw_b_sec_security,
                "privacy" to I18nR.string.netfw_b_sec_privacy,
            )
            val known = sections.map { it.first }.toSet()
            val all = sections + ("" to I18nR.string.netfw_b_sec_other)
            all.forEach { (section, title) ->
                val inSection = groups.filter { (if (section.isEmpty()) it.section !in known else it.section == section) && (groupHit(it) || it.lists.any { l -> listHit(it, l) }) }
                if (inSection.isEmpty()) return@forEach
                item(key = "section-$section") {
                    PreferenceCategory(stringResource(title)) {
                        inSection.forEach { group ->
                            GroupRows(
                                group = group,
                                enabled = enabled,
                                counts = counts,
                                expanded = expanded == group.id || bq.isNotBlank(),
                                listFilter = { l -> listHit(group, l) },
                                number = number,
                                onToggleExpanded = { expanded = if (expanded == group.id) null else group.id },
                                onGroup = { on -> scope.launch { blocklists.setGroupEnabled(group.id, on) } },
                                onList = { id, on -> scope.launch { blocklists.setEnabled(id, on) } },
                            )
                        }
                    }
                }
            }
            if (bq.isNotBlank() && groups.none { g -> groupHit(g) || g.lists.any { l -> listHit(g, l) } } && shownBypass.isEmpty()) {
                item { de.mm20.launcher2.ui.component.SearchEmptyState(bq.trim()) }
            }
            item {
                var confirmResetCounts by remember { mutableStateOf(false) }
                if (confirmResetCounts) {
                    AlertDialog(
                        onDismissRequest = { confirmResetCounts = false },
                        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au4_confirms_counts_title))  } },
                        text = { Text(stringResource(I18nR.string.au4_confirms_counts_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmResetCounts = false
                                scope.launch { blocklists.resetCounts() }
                            }) { Text(stringResource(I18nR.string.au4_confirms_confirm)) }
                        },
                        dismissButton = { TextButton(onClick = { confirmResetCounts = false }) { Text(stringResource(I18nR.string.au4_confirms_cancel)) } },
                    )
                }
                PreferenceCategory {
                    Preference(
                        title = stringResource(I18nR.string.netfw_b_reset_counts),
                        icon = IconsNetworkBlocklistsScreen.settings_backup_restore_24px,
                        onClick = { confirmResetCounts = true },
                    )
                }
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.netfw_b_bypass)) {
                Text(
                    stringResource(I18nR.string.netfw_b_bypass_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Preference(title = stringResource(I18nR.string.netfw_b_bypass_add), icon = IconsNetworkBlocklistsScreen.add_24px, onClick = { addBypass = true })
                shownBypass.forEach { b ->
                    Preference(
                        title = { Text(b.domain) },
                        summary = {
                            Text(
                                when (val s = b.scope) {
                                    RuleScope.System -> stringResource(I18nR.string.netfw_c_all_apps)
                                    is RuleScope.App -> directory.labelFor(s.appId)
                                }
                            )
                        },
                        controls = {
                            IconButton(onClick = { scope.launch { blocklists.removeBypass(b.id) } }) {
                                Icon(painterResource(IconsNetworkBlocklistsScreen.delete_24px), contentDescription = stringResource(I18nR.string.hc_delete))
                            }
                        },
                    )
                }
                if (bypass.isEmpty() && bq.isBlank()) {
                    Text(
                        stringResource(I18nR.string.netfw_b_bypass_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (addBypass) {
        AddRuleDialog(
            isIp = false,
            directory = directory,
            title = stringResource(I18nR.string.netfw_b_bypass_add),
            showAction = false,
            onDismiss = { addBypass = false },
            onAdd = { domain, _, _, ruleScope, _ ->
                scope.launch {
                    if (blocklists.addBypass(ruleScope, domain).isSuccess) addBypass = false
                    else android.widget.Toast.makeText(context, context.getString(I18nR.string.netfw_c_invalid), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun GroupRows(
    group: BlocklistGroup,
    enabled: Set<String>,
    counts: Map<String, Long>,
    expanded: Boolean,
    number: NumberFormat,
    onToggleExpanded: () -> Unit,
    onGroup: (Boolean) -> Unit,
    onList: (String, Boolean) -> Unit,
    listFilter: (Blocklist) -> Boolean = { true },
) {
    val on = group.lists.count { it.id in enabled }
    val domains = group.lists.sumOf { it.entryCount }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Preference(
            title = groupName(group),
            summary = stringResource(I18nR.string.netfw_b_selected, on, group.lists.size) + " · " +
                stringResource(I18nR.string.netfw_b_entries, number.format(domains)),
            onClick = onToggleExpanded,
            controls = { Switch(checked = on == group.lists.size, onCheckedChange = onGroup) },
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                group.lists.filter(listFilter).forEach { list ->
                    val blocked = counts[list.id] ?: 0L
                    SwitchPreference(
                        title = list.name,
                        summary = stringResource(I18nR.string.netfw_b_entries, number.format(list.entryCount)) +
                            if (blocked > 0) " · " + stringResource(I18nR.string.netfw_b_blocked_count, number.format(blocked)) else "",
                        value = list.id in enabled,
                        onValueChanged = { onList(list.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun groupName(group: BlocklistGroup): String {
    val sub = group.id.substringAfter('/')
    val res = when (sub) {
        "porn" -> I18nR.string.netfw_g_porn
        "piracy" -> I18nR.string.netfw_g_piracy
        "gambling" -> I18nR.string.netfw_g_gambling
        "services" -> I18nR.string.netfw_g_services
        "threat-intelligence-feeds" -> I18nR.string.netfw_g_threat
        "cryptojacking" -> I18nR.string.netfw_g_cryptojacking
        "bypass-methods" -> I18nR.string.netfw_g_bypass_methods
        "safesearch" -> I18nR.string.netfw_g_safesearch
        "dating" -> I18nR.string.netfw_g_dating
        "social-networks" -> I18nR.string.netfw_g_social
        "tracking-domains" -> I18nR.string.netfw_g_tracking
        "rethinkdns-recommended" -> I18nR.string.netfw_g_recommended
        "native" -> I18nR.string.netfw_g_native
        "others" -> I18nR.string.netfw_g_others
        else -> null
    }
    return if (res != null) stringResource(res) else group.name
}
