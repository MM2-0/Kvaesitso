package dev.intelia.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.base.R as BaseR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteliaSettings(
    model: InteliaHomeViewModel,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDefaultHome: () -> Unit,
    onLegal: () -> Unit,
    onHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preferences by model.ui.collectAsStateWithLifecycle()
    val isDefault by model.isDefaultHome.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val packs by model.iconPacks.collectAsStateWithLifecycle()
    var appearance by remember { mutableIntStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(appearance) { listState.scrollToItem(0) }
    androidx.activity.compose.BackHandler(enabled = appearance != 0) { appearance = if (appearance > 1) 1 else 0 }
    ModalBottomSheet(onDismissRequest = { if (appearance == 0) onDismiss() else appearance = if (appearance > 1) 1 else 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 700.dp), state = listState, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (appearance == 0) {
            item {
                Text("Intelia Launcher", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text("0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            item { Text(stringResource(R.string.intelia_local_only), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
            item {
                FilledTonalButton(onClick = onDefaultHome, enabled = !isDefault,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Text(stringResource(if (isDefault) R.string.intelia_default_active else R.string.intelia_default_home), modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            item { HorizontalDivider() }
            item {
                TextButton(onClick = { appearance = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.intelia_customize))
                }
            }
            item {
                PreferenceToggle(stringResource(R.string.intelia_island), preferences.island, model::island)
                Text(stringResource(R.string.intelia_island_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preferences.island) {
                    val context = LocalContext.current
                    Text(stringResource(R.string.intelia_island_privacy), Modifier.padding(top = 12.dp), fontSize = 13.sp)
                    TextButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}"))) }
                    }) { Text(stringResource(R.string.intelia_island_overlay)) }
                    TextButton(onClick = {
                        runCatching {
                            val intent = if (android.os.Build.VERSION.SDK_INT >= 30) android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                    android.content.ComponentName(context, InteliaIslandService::class.java).flattenToString())
                            else android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        }
                    }) { Text(stringResource(R.string.intelia_island_media)) }
                }
            }
            item { TextButton(onClick = onHidden, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intelia_hidden)) } }
            item { HorizontalDivider() }
            item {
                OutlinedButton(onClick = onBackup, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.intelia_backup)) }
                OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.intelia_restore)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            item { TextButton(onClick = onLegal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intelia_legal)) } }
            } else {
                item { Text(stringResource(R.string.intelia_customize), style = MaterialTheme.typography.headlineSmall) }
                item { TextButton(onClick = { appearance = if (appearance > 1) 1 else 0 }) { Text(stringResource(R.string.intelia_back)) } }
                when (appearance) {
                    1 -> {
                        listOf(R.string.intelia_colors, R.string.intelia_icons, R.string.intelia_typography, R.string.intelia_interface).forEachIndexed { index, label ->
                            item { OutlinedButton(onClick = { appearance = index + 2 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) } }
                        }
                    }
                    2 -> {
            item {
                Text(stringResource(R.string.intelia_colors), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.intelia_system, R.string.intelia_dark, R.string.intelia_light).forEachIndexed { index, label ->
                        FilterChip(selected = preferences.theme == index, onClick = { model.theme(index) }, label = { Text(stringResource(label)) })
                    }
                }
            }

                        item { AppearanceChoices(R.string.intelia_palette, preferences.palette,
                            listOf(R.string.intelia_material_you, R.string.intelia_mono, R.string.intelia_sage, R.string.intelia_slate), model::palette) }
                    }
                    3 -> {
                        item { AppearanceChoices(R.string.intelia_icons, preferences.icons, inteliaIconLabels, model::icons) }
                        item { Text(stringResource(R.string.intelia_icon_pack_hint), style = MaterialTheme.typography.bodySmall) }
                        if (packs.isEmpty()) item { Text(stringResource(R.string.intelia_no_icon_packs)) }
                        items(packs, key = { it.packageName }) { pack ->
                            FilterChip(selected = preferences.icons == 3 && preferences.iconPack == pack.packageName,
                                onClick = { model.iconPack(pack.packageName) }, label = { Text(pack.name) })
                        }
                    }
                    4 -> {
                        item { AppearanceChoices(R.string.intelia_typography, preferences.typeface,
                            listOf(R.string.intelia_font_system, R.string.intelia_font_serif), model::typeface) }
                        item { PreferenceToggle(stringResource(R.string.intelia_clock), preferences.showClock, model::clock) }
                        item { PreferenceToggle(stringResource(R.string.intelia_date), preferences.showDate, model::date) }
                    }
                    5 -> {
            item {
                Text(stringResource(R.string.intelia_wallpaper), style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.intelia_wallpaper_auto, R.string.intelia_wallpaper_clear, R.string.intelia_wallpaper_solid).forEachIndexed { index, label ->
                        FilterChip(selected = preferences.wallpaper == index, onClick = { model.wallpaper(index) }, label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(R.string.intelia_wallpaper_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { PreferenceToggle(stringResource(R.string.intelia_reduce_motion), preferences.reduceMotion, model::motion) }
            item {
                Text(stringResource(R.string.intelia_reach), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.intelia_reach_center, R.string.intelia_reach_left, R.string.intelia_reach_right).forEachIndexed { index, text ->
                        FilterChip(selected = preferences.hand == index, onClick = { model.hand(index) }, label = { Text(stringResource(text)) })
                    }
                }
            }

                    }
                }
            }
        }
    }
}

internal val inteliaIconLabels = listOf(R.string.intelia_icon_system, R.string.intelia_icon_intelia, R.string.intelia_mono, R.string.intelia_icon_custom)

@Composable
private fun AppearanceChoices(title: Int, selected: Int, choices: List<Int>, change: (Int) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { index, label ->
            FilterChip(selected = selected == index, onClick = { change(index) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun PreferenceToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteliaLegal(onSource: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var license by remember { mutableStateOf<Int?>(null) }
    val paragraphs = remember(license) {
        license?.let { context.resources.openRawResource(it).bufferedReader().use { reader -> reader.readText() }.split("\n\n") }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 700.dp), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.intelia_legal), style = MaterialTheme.typography.headlineSmall) }
            if (paragraphs != null) {
                item { TextButton(onClick = { license = null }) { Text(stringResource(R.string.intelia_close)) } }
                items(paragraphs) { paragraph -> Text(paragraph, fontSize = 13.sp, lineHeight = 19.sp) }
            } else {
                item { Text(stringResource(R.string.intelia_legal_notice), fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { FilledTonalButton(onClick = onSource, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intelia_source)) } }
                item { OutlinedButton(onClick = { license = BaseR.raw.license_gpl_3 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intelia_gpl)) } }
                item { HorizontalDivider(); Text(stringResource(R.string.intelia_components), modifier = Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium) }
                items(OpenSourceLicenses.toList(), key = { it.name }) { library ->
                    Surface(onClick = { license = library.licenseText }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(library.name, fontWeight = FontWeight.Medium)
                            Text(stringResource(library.licenseName), modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            library.copyrightNote?.let { Text(it, modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}
