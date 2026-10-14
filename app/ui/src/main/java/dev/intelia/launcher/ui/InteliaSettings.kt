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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 700.dp), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                Text(stringResource(R.string.intelia_appearance), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.intelia_system, R.string.intelia_dark, R.string.intelia_light).forEachIndexed { index, label ->
                        FilterChip(selected = preferences.theme == index, onClick = { model.theme(index) }, label = { Text(stringResource(label)) })
                    }
                }
            }
            item { PreferenceToggle(stringResource(R.string.intelia_clock), preferences.showClock, model::clock) }
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
            item { TextButton(onClick = onHidden, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intelia_hidden)) } }
            item { HorizontalDivider() }
            item {
                OutlinedButton(onClick = onBackup, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.intelia_backup)) }
                OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.intelia_restore)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            item { TextButton(onClick = onLegal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intelia_legal)) } }
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
