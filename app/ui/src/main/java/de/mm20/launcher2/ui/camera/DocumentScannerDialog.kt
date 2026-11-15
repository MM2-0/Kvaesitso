package de.mm20.launcher2.ui.camera

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DocumentScannerDialog(vm: DocumentScannerViewModel, onDismiss: () -> Unit, onCapture: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Dialog owns a separate window; retain the host's user font density there.
    val hostDensity = androidx.compose.ui.platform.LocalDensity.current
    val pages by vm.pages.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val progress by vm.progress.collectAsState()
    val export by vm.export.collectAsState()
    val saved by vm.saved.collectAsState()
    val languages by vm.languages.collectAsState()
    var languagePicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addImages(it) }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { vm.saveExport(it) }
    LaunchedEffect(export) { if (export != null) destination.launch("Telos-scan.pdf") }
    var previousSaved by remember { mutableIntStateOf(saved) }
    LaunchedEffect(saved) { if (saved > previousSaved) Toast.makeText(context, I18nR.string.camera_native_saved, Toast.LENGTH_SHORT).show(); previousSaved = saved }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides hostDensity) {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val compactActions = maxHeight < 520.dp || maxWidth < 400.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.5f
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back)) }
                    Text(stringResource(I18nR.string.camera_scan_pdf), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { languagePicker = true }, enabled = !busy) { Icon(painterResource(BaseR.drawable.language_24px), stringResource(I18nR.string.camera_scan_ocr)) }
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(I18nR.string.camera_scan_processing, progress, pages.size), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (error) {
                    TextButton(onClick = vm::dismissError) { Text(stringResource(I18nR.string.pdft_error_title), color = MaterialTheme.colorScheme.error) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(stringResource(I18nR.string.camera_scan_hint), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { languagePicker = true }, enabled = !busy) {
                            Text(if (languages.isEmpty()) stringResource(I18nR.string.camera_scan_no_ocr) else stringResource(I18nR.string.camera_scan_ocr) + " · " + languages.sorted().joinToString(" + "))
                        }
                    }
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val preview by produceState<Bitmap?>(null, page) {
                                    value = withContext(Dispatchers.IO) { runCatching {
                                        val bitmap = DocumentScanEngine.render(page)
                                        val scale = minOf(1f, 640f / maxOf(bitmap.width, bitmap.height))
                                        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                                        if (small !== bitmap) bitmap.recycle()
                                        small
                                    }.getOrNull() }
                                }
                                preview?.let { Image(it.asImageBitmap(), stringResource(I18nR.string.hc_page_number, index + 1), Modifier.fillMaxWidth().heightIn(max = 200.dp)) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(I18nR.string.hc_page_number, index + 1), Modifier.weight(1f))
                                    IconButton(onClick = { vm.move(page.id, -1) }, enabled = index > 0 && !busy) { Icon(painterResource(BaseR.drawable.keyboard_arrow_up_24px), stringResource(I18nR.string.pdft_move_up)) }
                                    IconButton(onClick = { vm.move(page.id, 1) }, enabled = index < pages.lastIndex && !busy) { Icon(painterResource(BaseR.drawable.keyboard_arrow_down_24px), stringResource(I18nR.string.pdft_move_down)) }
                                    IconButton(onClick = { vm.remove(page.id) }, enabled = !busy) { Icon(painterResource(BaseR.drawable.delete_24px), stringResource(I18nR.string.pdft_remove)) }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { vm.edit(page.copy(turns = (page.turns + 1).mod(4))) }, enabled = !busy) { Text(stringResource(I18nR.string.hc_rotate_right)) }
                                    TextButton(onClick = { expanded = if (expanded == page.id) null else page.id }, enabled = !busy) { Text(stringResource(I18nR.string.screenshot_tool_crop)) }
                                    FilterChip(selected = page.grayscale, onClick = { vm.edit(page.copy(grayscale = !page.grayscale)) }, enabled = !busy, label = { Text(stringResource(I18nR.string.pdft_tool_grayscale)) })
                                }
                                if (expanded == page.id) {
                                    var x by remember(page.cropX) { mutableFloatStateOf(page.cropX) }
                                    var y by remember(page.cropY) { mutableFloatStateOf(page.cropY) }
                                    Text(stringResource(I18nR.string.camera_scan_crop_width))
                                    Slider(value = x, onValueChange = { x = it }, onValueChangeFinished = { vm.edit(page.copy(cropX = x)) }, valueRange = 0f..0.4f, enabled = !busy)
                                    Text(stringResource(I18nR.string.camera_scan_crop_height))
                                    Slider(value = y, onValueChange = { y = it }, onValueChangeFinished = { vm.edit(page.copy(cropY = y)) }, valueRange = 0f..0.4f, enabled = !busy)
                                }
                            }
                        }
                    }
                }
                if (compactActions) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilledTonalIconButton(onClick = onCapture, enabled = !busy && pages.size < DocumentScanEngine.MAX_PAGES) {
                            Icon(painterResource(BaseR.drawable.add_24px), stringResource(I18nR.string.camera_scan_add_page))
                        }
                        FilledTonalIconButton(onClick = { images.launch(arrayOf("image/*")) }, enabled = !busy && pages.size < DocumentScanEngine.MAX_PAGES) {
                            Icon(painterResource(BaseR.drawable.photo_24px), stringResource(I18nR.string.pdft_add_images))
                        }
                        FilledIconButton(onClick = vm::createPdf, enabled = !busy && pages.isNotEmpty()) {
                            Icon(painterResource(BaseR.drawable.save_24px), stringResource(I18nR.string.pdft_save_as))
                        }
                    }
                } else FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onCapture, enabled = !busy && pages.size < DocumentScanEngine.MAX_PAGES) { Text(stringResource(I18nR.string.camera_scan_add_page)) }
                    OutlinedButton(onClick = { images.launch(arrayOf("image/*")) }, enabled = !busy && pages.size < DocumentScanEngine.MAX_PAGES) { Text(stringResource(I18nR.string.pdft_add_images)) }
                    Button(onClick = vm::createPdf, enabled = !busy && pages.isNotEmpty()) { Text(stringResource(I18nR.string.pdft_save_as)) }
                }
            }
            }
        }
    }
    }
    if (languagePicker) {
        var search by remember { mutableStateOf("") }
        val options = remember(search) { CameraOcrModels.languages.filter { it.name.contains(search, true) || it.code.contains(search, true) }.sortedBy { it.name } }
        AlertDialog(onDismissRequest = { languagePicker = false }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.camera_scan_ocr))  } },
            text = { Column {
                Text(stringResource(I18nR.string.camera_scan_ocr_hint))
                OutlinedTextField(search, { search = it }, label = { Text(stringResource(I18nR.string.au12_tvui_languages)) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    item { Row(Modifier.fillMaxWidth().toggleable(languages.isEmpty(), enabled = !busy) { vm.selectLanguages(emptySet()) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(languages.isEmpty(), null); Text(stringResource(I18nR.string.camera_scan_no_ocr))
                    } }
                    itemsIndexed(options, key = { _, language -> language.code }) { _, language ->
                        val selected = language.code in languages
                        Row(Modifier.fillMaxWidth().toggleable(selected, enabled = !busy && (selected || languages.size < 3)) { vm.selectLanguages(if (selected) languages - language.code else languages + language.code) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(selected, null); Text(language.name + " · " + language.code, Modifier.weight(1f))
                        }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { languagePicker = false }) { Text(stringResource(I18nR.string.hc_close)) } })
    }
}
