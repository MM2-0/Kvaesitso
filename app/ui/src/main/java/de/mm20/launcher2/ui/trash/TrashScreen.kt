package de.mm20.launcher2.ui.trash

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import de.mm20.launcher2.ui.component.virtualAppPopup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.comms.files.DiskCleaner
import de.mm20.launcher2.comms.files.DiskCleanerWorker
import de.mm20.launcher2.comms.files.TelosTrash
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen() {
    val trash: TelosTrash = koinInject()
    val context = LocalContext.current
    val cleaner = remember { DiskCleaner(context) }
    val scope = rememberCoroutineScope()
    val backStack = LocalBackStack.current
    val t = MaterialTheme.transparency
    val card = MaterialTheme.colorScheme.surface.copy(alpha = t.surface)
    val bar = MaterialTheme.colorScheme.surface.copy(alpha = t.elevatedSurface)

    var bin by remember { mutableStateOf<List<TelosTrash.Item>>(emptyList()) }
    var junk by remember { mutableStateOf<List<DiskCleaner.Junk>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var warnPriv by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf(cleaner.intervalHours) }
    var charging by remember { mutableStateOf(cleaner.onlyCharging) }
    var skipSave by remember { mutableStateOf(cleaner.skipPowerSave) }
    var priv by remember { mutableStateOf(cleaner.privileged) }
    val selected = remember { mutableStateMapOf<DiskCleaner.Kind, Boolean>() }

    fun reloadBin() {
        scope.launch { bin = trash.entries() }
    }
    LaunchedEffect(Unit) { reloadBin() }

    val groups = remember(junk) {
        DiskCleaner.Kind.entries.mapNotNull { k ->
            val list = junk.filter { it.kind == k }
            if (list.isEmpty()) null else Triple(k, list.size, list.sumOf { it.size })
        }
    }
    val selectedBytes = groups.filter { selected[it.first] != false }.sumOf { it.third }
    val selectedCount = groups.filter { selected[it.first] != false }.sumOf { it.second }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = t.background))
                .blur(28.dp),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        TelosCyberHeading {
                            Column {
                                Text(stringResource(I18nR.string.telos_trash_title))
                                TelosDotMatrixText(
                                    stringResource(if (junk.isNotEmpty()) I18nR.string.trash_hero_ready else I18nR.string.trash_hero_bin),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = null)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showSchedule = !showSchedule }) {
                            Text(stringResource(I18nR.string.trash_clean_schedule))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bar),
                )
            },
        ) { pad ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
                val sideBySide = AdaptiveLayoutPolicy.sideBySide(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    if (sideBySide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HeroCard(
                                modifier = Modifier.weight(1f),
                                readyBytes = if (junk.isEmpty()) bin.sumOf { it.entry.file.length() } else selectedBytes,
                                readyCount = if (junk.isEmpty()) bin.size else selectedCount,
                                binCount = bin.size,
                                scanned = junk.isNotEmpty(),
                            )
                            TrashActionRow(
                                modifier = Modifier.weight(1f),
                                busy = busy,
                                scanning = scanning,
                                junkNotEmpty = junk.isNotEmpty(),
                                selectedCount = selectedCount,
                                status = status,
                                onScan = {
                                    scope.launch {
                                        scanning = true
                                        busy = true
                                        status = ""
                                        junk = withContext(Dispatchers.IO) { cleaner.scan() }
                                        DiskCleaner.Kind.entries.forEach { selected[it] = true }
                                        scanning = false
                                        busy = false
                                    }
                                },
                                onFill = {
                                    scope.launch {
                                        busy = true
                                        val chosen = junk.filter { selected[it.kind] != false }
                                        val r = withContext(Dispatchers.IO) { cleaner.fillTrash(trash, chosen) }
                                        status = context.getString(I18nR.string.trash_clean_done, r.moved, r.deleted, r.bytes / 1024 / 1024)
                                        junk = emptyList()
                                        reloadBin()
                                        busy = false
                                    }
                                },
                            )
                        }
                    } else {
                        HeroCard(
                            readyBytes = if (junk.isEmpty()) bin.sumOf { it.entry.file.length() } else selectedBytes,
                            readyCount = if (junk.isEmpty()) bin.size else selectedCount,
                            binCount = bin.size,
                            scanned = junk.isNotEmpty(),
                        )
                    }
                }
                if (!sideBySide) {
                item {
                    TrashActionRow(
                        busy = busy,
                        scanning = scanning,
                        junkNotEmpty = junk.isNotEmpty(),
                        selectedCount = selectedCount,
                        status = status,
                        onScan = {
                            scope.launch {
                                scanning = true
                                busy = true
                                status = ""
                                junk = withContext(Dispatchers.IO) { cleaner.scan() }
                                DiskCleaner.Kind.entries.forEach { selected[it] = true }
                                scanning = false
                                busy = false
                            }
                        },
                        onFill = {
                            scope.launch {
                                busy = true
                                val chosen = junk.filter { selected[it.kind] != false }
                                val r = withContext(Dispatchers.IO) { cleaner.fillTrash(trash, chosen) }
                                status = context.getString(I18nR.string.trash_clean_done, r.moved, r.deleted, r.bytes / 1024 / 1024)
                                junk = emptyList()
                                reloadBin()
                                busy = false
                            }
                        },
                    )
                }
                }
                if (groups.isNotEmpty()) {
                    item {
                        Text(stringResource(I18nR.string.trash_found), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    items(groups, key = { it.first.name }) { (kind, count, bytes) ->
                        val on = selected[kind] != false
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .telosTechCard(borderColor = MaterialTheme.colorScheme.primary, alpha = 0.35f)
                                .clickable { selected[kind] = !on }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = { selected[kind] = it })
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(kindLabel(kind), fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(I18nR.string.trash_kind_count, count, formatBytes(bytes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    AnimatedVisibility(showSchedule) {
                        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(20.dp)).padding(8.dp)) {
                            Text(
                                stringResource(I18nR.string.trash_clean_schedule),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(12.dp),
                            )
                            ListPreference(
                                title = stringResource(I18nR.string.trash_clean_interval),
                                items = listOf(
                                    stringResource(I18nR.string.trash_clean_off) to 0,
                                    stringResource(I18nR.string.trash_clean_hourly) to 1,
                                    stringResource(I18nR.string.trash_clean_daily) to 24,
                                    stringResource(I18nR.string.trash_clean_weekly) to 24 * 7,
                                    stringResource(I18nR.string.trash_clean_monthly) to 24 * 30,
                                ),
                                value = interval,
                                onValueChanged = {
                                    interval = it
                                    cleaner.intervalHours = it
                                    DiskCleanerWorker.enqueue(context)
                                },
                            )
                            SwitchPreference(
                                title = stringResource(I18nR.string.trash_clean_charging),
                                summary = stringResource(I18nR.string.trash_clean_charging_sum),
                                value = charging,
                                onValueChanged = {
                                    charging = it
                                    cleaner.onlyCharging = it
                                    DiskCleanerWorker.enqueue(context)
                                },
                            )
                            SwitchPreference(
                                title = stringResource(I18nR.string.trash_clean_powersave),
                                summary = stringResource(I18nR.string.trash_clean_powersave_sum),
                                value = skipSave,
                                onValueChanged = { skipSave = it; cleaner.skipPowerSave = it },
                            )
                            SwitchPreference(
                                title = stringResource(I18nR.string.trash_clean_privileged),
                                summary = stringResource(I18nR.string.trash_clean_privileged_sum),
                                value = priv,
                                onValueChanged = {
                                    if (it) warnPriv = true else {
                                        priv = false
                                        cleaner.privileged = false
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    Text(
                        stringResource(I18nR.string.trash_in_bin),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (bin.isEmpty() && !busy) {
                    item {
                        Text(
                            stringResource(I18nR.string.telos_trash_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(bin, key = { it.entry.file.path }) { item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .telosTechCard(borderColor = Color(0xFFFF007F), alpha = 0.35f)
                            .padding(14.dp),
                    ) {
                        Text(item.entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.entry.original, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(I18nR.string.telos_trash_expires, DateFormat.getDateInstance().format(Date(item.entry.expiresAt))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton(onClick = { scope.launch { trash.restore(item); reloadBin() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(I18nR.string.telos_trash_restore))
                            }
                            TextButton(onClick = { scope.launch { trash.deletePermanently(item); reloadBin() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(I18nR.string.telos_trash_permanent), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
    if (warnPriv) {
        AlertDialog(
            onDismissRequest = { warnPriv = false },
            containerColor = virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.trash_clean_priv_warn_title))  } },
            text = { Text(stringResource(I18nR.string.trash_clean_priv_warn_text)) },
            confirmButton = {
                TextButton(onClick = {
                    priv = true
                    cleaner.privileged = true
                    warnPriv = false
                }) { Text(stringResource(I18nR.string.trash_clean_priv_confirm)) }
            },
            dismissButton = { TextButton(onClick = { warnPriv = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrashActionRow(
    busy: Boolean,
    scanning: Boolean,
    junkNotEmpty: Boolean,
    selectedCount: Int,
    status: String,
    onScan: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilledTonalButton(
        onClick = onScan,
        enabled = !busy,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
      ) { Text(stringResource(I18nR.string.trash_scan)) }
      Button(
        onClick = onFill,
        enabled = !busy && junkNotEmpty && selectedCount > 0,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
      ) { Text(stringResource(I18nR.string.trash_fill_bin)) }
    }
    AnimatedVisibility(scanning || busy) {
      LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (status.isNotBlank()) {
      Text(status, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun HeroCard(
    readyBytes: Long,
    readyCount: Int,
    binCount: Int,
    scanned: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .telosTechCard(borderColor = Color(0xFF00F0FF), alpha = 0.5f)
            .padding(24.dp),
    ) {
        Text(
            if (scanned) stringResource(I18nR.string.trash_hero_ready) else stringResource(I18nR.string.trash_hero_bin),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatBytes(readyBytes),
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (scanned) stringResource(I18nR.string.trash_hero_items, readyCount)
            else stringResource(I18nR.string.trash_hero_stored, binCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .padding(top = 16.dp)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(BaseR.drawable.delete_24px), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun kindLabel(kind: DiskCleaner.Kind): String = stringResource(
    when (kind) {
        DiskCleaner.Kind.Cache -> I18nR.string.trash_kind_cache
        DiskCleaner.Kind.Temp -> I18nR.string.trash_kind_temp
        DiskCleaner.Kind.Log -> I18nR.string.trash_kind_log
        DiskCleaner.Kind.Thumbnail -> I18nR.string.trash_kind_thumb
        DiskCleaner.Kind.Apk -> I18nR.string.trash_kind_apk
        DiskCleaner.Kind.EmptyDir -> I18nR.string.trash_kind_empty
        DiskCleaner.Kind.Corpse -> I18nR.string.trash_kind_corpse
    }
)

private fun formatBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${n / 1024} KB"
    n < 1024L * 1024 * 1024 -> String.format("%.1f MB", n / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", n / (1024.0 * 1024 * 1024))
}
