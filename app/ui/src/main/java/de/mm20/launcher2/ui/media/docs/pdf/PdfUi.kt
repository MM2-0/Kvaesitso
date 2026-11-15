/*
 * Telos PDF tools.
 *
 * Adapted from PaperKnife+ (https://github.com/potatameister/PaperKnifePlus)
 * Copyright (C) potatameister and PaperKnife+ contributors
 * Copyright (C) 2026 Telos contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see the
 * GNU General Public License for more details (https://www.gnu.org/licenses/).
 *
 * PDF processing uses PdfBox-Android (https://github.com/TomRoush/PdfBox-Android, Apache-2.0).
 */

package de.mm20.launcher2.ui.media.docs.pdf

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

/** A PDF opened with the system's renderer. Only one page can be open at a time, so access is serialised. */
class PdfPages(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    val count: Int = renderer.pageCount
    private var closed = false

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(page: Int, width: Int): Bitmap? = cache.get("$page:$width")

    @Synchronized
    fun render(page: Int, width: Int): Bitmap {
        check(!closed) { "closed" }
        cache.get("$page:$width")?.let { return it }
        val bmp = renderer.openPage(page).use { p ->
            // never more than ~16 megapixels per bitmap (zoomed pages would otherwise run out of memory)
            val ratio = p.height / p.width.toFloat()
            val w = minOf(width.toFloat(), kotlin.math.sqrt(16_000_000f / ratio)).toInt().coerceAtLeast(1)
            val height = (w * ratio).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE)
                p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        cache.put("$page:$width", bmp)
        return bmp
    }

    // waits for a page that is being drawn: closing the renderer under it would crash
    @Synchronized
    fun close() {
        closed = true
        cache.evictAll()
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

/** Opens [file] off the main thread and closes it again when it leaves the composition. */
@Composable
fun rememberPdfPages(file: File?): PdfPages? {
    val state = produceState<PdfPages?>(null, file) {
        val opened = if (file == null) null else withContext(Dispatchers.IO) { runCatching { PdfPages(file) }.getOrNull() }
        value = opened
        awaitDispose { opened?.close() }
    }
    return state.value
}

@Composable
fun Ico(@DrawableRes id: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, description: String? = null) {
    Icon(painterResource(id), contentDescription = description, modifier = modifier, tint = tint)
}

@Composable
fun PdfThumb(
    pages: PdfPages,
    index: Int,
    modifier: Modifier = Modifier,
    rotation: Int = 0,
    widthPx: Int = 240,
) {
    val bmp by produceState<Bitmap?>(pages.cached(index, widthPx), pages, index, widthPx) {
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { pages.render(index, widthPx) }.getOrNull() }
    }
    BoxWithConstraints(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val aspect = b.width.toFloat() / b.height
            val dw = if (aspect > boxW / boxH) boxW else boxH * aspect
            val dh = if (aspect > boxW / boxH) boxW / aspect else boxH
            val quarter = rotation % 180 != 0
            val s = if (quarter) min(boxW / dh, boxH / dw).coerceAtMost(1f) else 1f
            Image(
                b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = rotation.toFloat()
                    scaleX = s
                    scaleY = s
                },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageGrid(
    pages: PdfPages,
    order: List<Int>,
    modifier: Modifier = Modifier,
    selected: Set<Int> = emptySet(),
    rotations: Map<Int, Int> = emptyMap(),
    faded: Set<Int> = emptySet(),
    label: (Int) -> String = { (it + 1).toString() },
    onLongClick: ((Int) -> Unit)? = null,
    onClick: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(order, key = { it }) { page ->
            val isSelected = page in selected
            Column(
                Modifier.combinedClickable(
                    onClick = { onClick(page) },
                    onLongClick = onLongClick?.let { handler -> { handler(page) } },
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f)
                        .alpha(if (page in faded) 0.35f else 1f)
                        .clip(MaterialTheme.shapes.small)
                        .border(
                            if (isSelected) 3.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.small,
                        )
                ) {
                    PdfThumb(pages, page, Modifier.fillMaxSize(), rotations[page] ?: 0)
                    if (isSelected) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Ico(BaseR.drawable.check_24px, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Text(label(page), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Full screen page viewer (lightbox) with swiping between pages, pinch to zoom and double tap to zoom. */
@Composable
fun PdfPreviewDialog(pages: PdfPages, startPage: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            val pager = rememberPagerState(initialPage = startPage.coerceIn(0, (pages.count - 1).coerceAtLeast(0))) { pages.count }
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { index -> ZoomablePage(pages, index) }
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Ico(BaseR.drawable.close_24px, tint = Color.White, description = stringResource(I18nR.string.pdft_close))
                    }
                    Surface(shape = MaterialTheme.shapes.large, color = Color.White.copy(alpha = 0.16f)) {
                        Text(
                            stringResource(I18nR.string.pdft_page_of, pager.currentPage + 1, pages.count),
                            Modifier.padding(12.dp, 6.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(pages: PdfPages, index: Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var zoom by remember { mutableFloatStateOf(1f) }
        val bucket = ceil(zoom).toInt().coerceAtLeast(1)
        val renderWidth = (constraints.maxWidth * bucket).coerceIn(300, 3200)
        val bmp by produceState<Bitmap?>(pages.cached(index, renderWidth), pages, index, renderWidth) {
            val fresh = withContext(Dispatchers.IO) { runCatching { pages.render(index, renderWidth) }.getOrNull() }
            if (fresh != null) value = fresh
        }
        val hScroll = rememberScrollState()
        val vScroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        Box(
            Modifier
                .fillMaxSize()
                .horizontalScroll(hScroll)
                .verticalScroll(vScroll)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { p ->
                        // zoom around the tapped point (content coordinates)
                        val new = if (zoom > 1.05f) 1f else 2.5f
                        val f = new / zoom
                        val tx = ((p.x * f) - (p.x - hScroll.value)).toInt().coerceAtLeast(0)
                        val ty = ((p.y * f) - (p.y - vScroll.value)).toInt().coerceAtLeast(0)
                        zoom = new
                        scope.launch {
                            androidx.compose.runtime.withFrameNanos { }
                            hScroll.scrollTo(tx)
                            vScroll.scrollTo(ty)
                        }
                    })
                }
                .pointerInput(Unit) {
                    // only a pinch is taken here, one finger still scrolls or swipes
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val old = zoom
                                val new = (old * event.calculateZoom()).coerceIn(1f, 5f)
                                val f = new / old
                                if (f != 1f) {
                                    // zoom around the point between the fingers (content coordinates)
                                    val c = event.calculateCentroid(useCurrent = true)
                                    val tx = ((c.x * f) - (c.x - hScroll.value)).toInt().coerceAtLeast(0)
                                    val ty = ((c.y * f) - (c.y - vScroll.value)).toInt().coerceAtLeast(0)
                                    zoom = new
                                    scope.launch {
                                        androidx.compose.runtime.withFrameNanos { }
                                        hScroll.scrollTo(tx)
                                        vScroll.scrollTo(ty)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val b = bmp
            if (b != null) {
                val aspect = b.width.toFloat() / b.height
                val fitW = minOf(this@BoxWithConstraints.maxWidth, this@BoxWithConstraints.maxHeight * aspect)
                val w = fitW * zoom
                val h = w / aspect
                Box(Modifier.size(maxOf(w, this@BoxWithConstraints.maxWidth), maxOf(h, this@BoxWithConstraints.maxHeight)), contentAlignment = Alignment.Center) {
                    Image(b.asImageBitmap(), null, Modifier.size(w, h), contentScale = ContentScale.FillBounds)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- screen building blocks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolFrame(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { TelosCyberHeading {  Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)  } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Ico(BaseR.drawable.arrow_back_24px, description = stringResource(I18nR.string.pdft_back))
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
fun ActionBar(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    secondary: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            secondary?.invoke(this)
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(label) }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
fun FileHeader(src: PdfSource, onChange: (() -> Unit)?, onPreview: (() -> Unit)?, onRemove: (() -> Unit)? = null) {
    val context = LocalContext.current
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(src.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (src.broken) stringResource(I18nR.string.pdft_file_broken, formatBytes(context, src.sizeBytes))
                    else stringResource(I18nR.string.pdft_file_info, src.pageCount, formatBytes(context, src.sizeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onPreview != null && !src.broken) {
                IconButton(onClick = onPreview) {
                    Ico(BaseR.drawable.visibility_24px, description = stringResource(I18nR.string.pdft_preview))
                }
            }
            if (onChange != null) TextButton(onClick = onChange) { Text(stringResource(I18nR.string.pdft_change)) }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Ico(BaseR.drawable.close_24px, description = stringResource(I18nR.string.pdft_remove))
                }
            }
        }
    }
}

@Composable
fun EmptyPick(text: String, button: String, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ico(BaseR.drawable.description_24px, Modifier.size(48.dp))
        Text(
            text,
            Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPick) { Text(button) }
    }
}

@Composable
fun PasswordDialog(name: String, wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.pdft_password_title))  } },
        text = {
            Column {
                Text(stringResource(I18nR.string.pdft_password_text, name), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                    label = { Text(stringResource(I18nR.string.pdft_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(I18nR.string.pdft_password_wrong)) }) else null,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(stringResource(I18nR.string.pdft_unlock_action)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(I18nR.string.pdft_cancel)) } },
    )
}

/** Returns a function that opens the system picker for PDFs. */
@Composable
fun rememberPdfPicker(multiple: Boolean, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(listOf(uri))
    }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return { if (multiple) many.launch(arrayOf("application/pdf")) else single.launch(arrayOf("application/pdf")) }
}

@Composable
fun rememberImagePicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return { many.launch(arrayOf("image/*")) }
}

@Composable
fun rememberSingleImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { single.launch(arrayOf("image/*")) }
}

@Composable
fun ColumnScrollBody(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
}

/** Shows the lightbox for [src] while [page] is not null. */
@Composable
fun PreviewHost(src: PdfSource?, page: Int?, onDismiss: () -> Unit) {
    if (src == null || page == null) return
    val pages = rememberPdfPages(src.file)
    if (pages != null) PdfPreviewDialog(pages, page, onDismiss)
}
